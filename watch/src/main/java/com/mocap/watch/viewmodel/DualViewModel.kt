package com.mocap.watch.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime


enum class StreamState {
    Idle, // app waits for user to trigger the streaming
    Error, // error state. Stop using the app,
    Streaming // streaming to Phone
}


class DualViewModel(application: Application) :
    AndroidViewModel(application),
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "DualViewModel"  // for logging
        private const val STREAM_INTERVAL = 10L
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private val _connectedNodeDisplayName = MutableStateFlow("none")
    val nodeName = _connectedNodeDisplayName.asStateFlow()

    private val _connectedActiveApp = MutableStateFlow(false)
    val appActive = _connectedActiveApp.asStateFlow()

    private val _streamState = MutableStateFlow(StreamState.Idle)
    val streamState = _streamState.asStateFlow()

    // callbacks will write to these variables
    private var _rotVec: FloatArray = FloatArray(5) // Rotation Vector sensor or estimation
    private var _lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var _accl: FloatArray = FloatArray(3) // raw acceleration
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var _hrRaw: FloatArray = FloatArray(16) // Samsung's Raw HR data
    private var _gyro: FloatArray = FloatArray(3) // gyroscope
    private var _magn: FloatArray = FloatArray(3) // magnetic

    override fun onCleared() {
        _scope.cancel()
        _streamState.value = StreamState.Idle
    }

    fun onChannelClose(channel: Channel) {
        Log.d(TAG, "Closed Channel to ${channel.nodeId}")
        _streamState.value = StreamState.Idle
    }

    fun sendTestMessage() {
        _scope.launch {
            // Connect to phone node
            val task = _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
            val res = Tasks.await(task)
            val phoneNodes = res.getValue(DataSingleton.PHONE_APP_ACTIVE).nodes
            // throw error if more than one phone connected or no phone connected
            if ((phoneNodes.count() > 1) || (phoneNodes.isEmpty())) {
                throw Exception(
                    "0 or more than 1 node with active phone app detected. " +
                            "List of available nodes: $phoneNodes"
                )
            }
            val node = phoneNodes.first()
            Log.d(TAG, "Found node ${node.id}")

            // feed into byte buffer
            val msgData = DataSingleton.forwardQuat.value +
                    floatArrayOf(DataSingleton.CALIB_PRESS.value)
            val buffer = ByteBuffer.allocate(4 * msgData.size) // [quat, pres]
            for (v in msgData) buffer.putFloat(v)

            // send byte array in a message
            val sendMessageTask = _messageClient.sendMessage(
                node.id, DataSingleton.CALIBRATION_PATH, buffer.array()
            )
            val msgRes = Tasks.await(sendMessageTask)
            Log.d(TAG, "Sent Calibration message to ${node.id}")
        }
    }

    fun streamTrigger(checked: Boolean) {
        if (!checked) {
            // stop streaming
            _streamState.value = StreamState.Idle
            return
        } else {
            // otherwise, start a streaming channel
            Thread {
                // Connect to phone node
                val task = _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
                val res = Tasks.await(task)
                val phoneNodes = res.getValue(DataSingleton.PHONE_APP_ACTIVE).nodes
                // throw error if more than one phone connected or no phone connected
                if ((phoneNodes.count() > 1) || (phoneNodes.isEmpty())) {
                    _streamState.value = StreamState.Error
                    throw Exception(
                        "0 or more than 1 node with active phone app detected. " +
                                "List of available nodes: $phoneNodes"
                    )
                }
                val node = phoneNodes.first()

                // open channel
                val channelTask = _channelClient.openChannel(node.id, DataSingleton.CHANNEL_PATH)
                val channel = Tasks.await(channelTask)
                Log.d(TAG, "Opened Channel to ${channel.nodeId}")

                // get output stream
                val streamTask = _channelClient.getOutputStream(channel)
                val stream = Tasks.await(streamTask)

                // start the stream loop
                _streamState.value = StreamState.Streaming
                val streamStartTimeStamp = LocalDateTime.now()
                while (streamState.value == StreamState.Streaming) {
                    val diff =
                        Duration.between(
                            streamStartTimeStamp,
                            LocalDateTime.now()
                        ).toMillis()

                    // compose message as float array
                    val sensorData = floatArrayOf(diff.toFloat()) +
                            _rotVec + // transformed rotation vector[4] is a quaternion [w,x,y,z]
                            _lacc + // [3] linear acceleration x,y,z
                            _pres + // [1] atmospheric pressure
                            _grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                            _gyro + // [3] gyro data for time series prediction
                            _hrRaw // [16] undocumented data from Samsung's Hr raw sensor

                    // feed into byte buffer
                    val buffer = ByteBuffer.allocate(4 * DataSingleton.WATCH_MESSAGE_SIZE)
                    for (v in sensorData) {
                        buffer.putFloat(v)
                    }

                    // write to output stream
                    stream.write(buffer.array(), 0, buffer.capacity())
                    Thread.sleep(STREAM_INTERVAL)
                }
                // while loop closed. Close stream and channel
                stream.close()

                _channelClient.close(channel)
                Log.d(TAG, "Closed Channel to ${channel.nodeId}")
            }
        }
    }

    fun queryCapabilities() {
        _scope.launch {
            try {
                val task = _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
                val res = Tasks.await(task)

                // default is "none" = no phone connected
                _connectedNodeDisplayName.value = "none"
                if (res.containsKey(DataSingleton.PHONE_CAPABILITY)) {
                    val phoneNodes = res.getValue(DataSingleton.PHONE_CAPABILITY).nodes
                    if (phoneNodes.count() > 1) {
                        throw Exception("More than one node with phone-capability detected: $phoneNodes")
                    } else if (phoneNodes.count() == 1) {
                        _connectedNodeDisplayName.value = phoneNodes.first().displayName
                    }
                }

                // default is false = app is not active
                _connectedActiveApp.value = false
                if (res.containsKey(DataSingleton.PHONE_APP_ACTIVE)) {
                    val activityNodes = res.getValue(DataSingleton.PHONE_APP_ACTIVE).nodes
                    if (activityNodes.count() == 1) {
                        _connectedActiveApp.value = true
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(TAG, "Querying nodes failed: $exception")
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name == DataSingleton.PHONE_APP_ACTIVE) {
            val nodes = capabilityInfo.nodes
            if (nodes.count() > 1) {
                throw Exception("More than one node with phone-capability detected: $nodes")
            } else {
                _connectedActiveApp.value = nodes.isNotEmpty()
            }
            Log.d(TAG, "Connected app active at : $nodes")
        }
        if (capabilityInfo.name == DataSingleton.PHONE_CAPABILITY) {
            val nodes = capabilityInfo.nodes
            if (nodes.count() > 1) {
                throw Exception("More than one node with phone-capability detected: $nodes")
            } else if (nodes.isEmpty()) {
                _connectedNodeDisplayName.value = "No device"
            } else {
                _connectedNodeDisplayName.value = nodes.first().displayName
            }
            Log.d(TAG, "Connected phone : $nodes")
        }
    }

    // Events
    /** sensor callbacks */
    // Individual sensor reads are triggered by their onValueChanged events
    fun onLaccReadout(newReadout: FloatArray) {
        _lacc = newReadout
    }

    fun onRotVecReadout(newReadout: FloatArray) {
        // newReadout is [x,y,z,w, confidence]
        // our preferred order system is [w,x,y,z]
        _rotVec = floatArrayOf(
            newReadout[3],
            newReadout[0],
            newReadout[1],
            newReadout[2]
        )
    }

    fun onAcclReadout(newReadout: FloatArray) {
        _accl = newReadout
    }

    fun onGyroReadout(newReadout: FloatArray) {
        _gyro = newReadout
    }

    fun onMagnReadout(newReadout: FloatArray) {
        _magn = newReadout
    }

    fun onHrRawReadout(newReadout: FloatArray) {
        _hrRaw = newReadout
    }

    fun onPressureReadout(newReadout: FloatArray) {
        _pres = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }

}
