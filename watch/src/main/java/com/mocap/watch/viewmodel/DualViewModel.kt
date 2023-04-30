package com.mocap.watch.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.ChannelIOException
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)


    // this will hold the connected Phone ID
    private var _connectedNodeId: String = "none"

    private val _connectedNodeDisplayName = MutableStateFlow("No Device")
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

    fun onChannelClose(c: Channel) {
        _streamState.value = StreamState.Idle
    }

    fun streamTrigger(checked: Boolean) {
        if (!checked) {
            _streamState.value = StreamState.Idle
        } else {

            if (streamState.value == StreamState.Streaming) {
                throw Exception("The StreamState.Streaming should not be active on start")
            }

            // otherwise, start a Channel in a coroutine
            _scope.launch {

                // Open the channel
                val channel = _channelClient.openChannel(
                    _connectedNodeId,
                    DataSingleton.CHANNEL_PATH
                ).await()
                Log.d(TAG, "Opened Channel to $_connectedNodeId")

                try {
                    // get output stream
                    val outputStream = _channelClient.getOutputStream(channel).await()
                    outputStream.use {

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
                            for (v in sensorData) buffer.putFloat(v)

                            // write to output stream
                            outputStream.write(buffer.array(), 0, buffer.capacity())
                            delay(STREAM_INTERVAL)
                        }
                    }
                } catch (e: CancellationException) {
                    // In case the scope gets cancelled while still in the loop
                    _channelClient.close(channel)
                    Log.d(TAG, "Unexpected scope cancel \n" + e.message.toString())
                    _streamState.value = StreamState.Error
                    return@launch
                } catch (e: Exception) {
                    // In case the channel gets destroyed while still in the loop
                    _channelClient.close(channel)
                    Log.d(TAG, e.message.toString())
                    _streamState.value = StreamState.Error
                    return@launch
                }

                // the while loop is done, close channel an reset stream state
                _channelClient.close(channel)
                Log.d(TAG, "Stream Trigger complete")
            }
        }
    }

    fun queryCapabilities() {
        _scope.launch {
            try {
                val task = _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
                val res = Tasks.await(task)
                // handling happens in the callback
                for ((_, v) in res.iterator()) {
                    onCapabilityChanged(v)
                }
            } catch (exception: Exception) {
                Log.d(TAG, "Querying nodes failed: $exception")
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val deviceCap = DataSingleton.PHONE_CAPABILITY
        val appCap = DataSingleton.PHONE_APP_ACTIVE
        // this checks if a phone is available at all
        if (capabilityInfo.name == deviceCap) {
            val nodes = capabilityInfo.nodes
            if (nodes.count() > 1) {
                throw Exception("More than one node with $deviceCap detected: $nodes")
            } else if (nodes.isEmpty()) {
                _connectedNodeDisplayName.value = "No device"
            } else {
                _connectedNodeDisplayName.value = nodes.first().displayName
            }
            Log.d(TAG, "Connected phone : $nodes")
        }

        // check if the app is running and open a communication channel if so
        if (capabilityInfo.name == appCap) {
            val nodes = capabilityInfo.nodes
            if (nodes.count() > 1) {
                throw Exception("More than one node with $appCap detected: $nodes")
            } else {
                _connectedActiveApp.value = nodes.isNotEmpty()
                if (nodes.isNotEmpty()) {
                    _connectedNodeId = nodes.first().id
                } else {
                    _connectedNodeId = "none"
                }
            }
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
