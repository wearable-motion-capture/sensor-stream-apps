package com.mocap.phone.viewmodel

import android.app.Application
import android.hardware.Sensor
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import com.mocap.phone.modules.SensorListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import kotlin.concurrent.thread

enum class StreamState {
    Idle, // app waits for watch to trigger the streaming
    Error, // error state. Stop streaming
    Streaming // streaming to IP and Port set in StateModule
}

class PhoneViewModel(application: Application) :
    AndroidViewModel(application),
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "PhoneViewModel"
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
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
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _gyro: FloatArray = FloatArray(3) // gyroscope
    private var _magn: FloatArray = FloatArray(3) // magnetic

    // query capabilities assigns a not here
    var connectedNode: Node? = null

    // sensor listeners are callbacks for the sensor manager
    val listeners = listOf(
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { onLaccReadout(it) },
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD
        ) { onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { onGyroReadout(it) }
    )


    override fun onCleared() {
        _scope.cancel()
        _streamState.value = StreamState.Idle
        Log.d(TAG, "Cleared")
    }

    fun onWatchChannelOpen(channel: Channel) {
        Log.d(TAG, "Channel opened by ${channel.nodeId}")
        thread {
            // get the input stream from the opened channel
            val streamTask = _channelClient.getInputStream(channel)
            val stream = Tasks.await(streamTask)

            _streamState.value = StreamState.Streaming
            while (streamState.value == StreamState.Streaming) {
                if (stream.available() > 0) {
                    // read input stream message into buffer
                    val buffer = ByteBuffer.allocate(4 * DataSingleton.WATCH_MESSAGE_SIZE)
                    stream.read(buffer.array(), 0, buffer.capacity())
                    Log.d(TAG, "${buffer.getFloat(0)} : ${stream.available()}")
                }
            }
            stream.close()
        }
    }

    fun onWatchChannelClose(channel: Channel) {
        _streamState.value = StreamState.Idle
        Log.d(TAG, "Channel closed by ${channel.nodeId}")
    }

    fun queryCapabilities() {

        val deviceCap = DataSingleton.WATCH_CAPABILITY
        val appCap = DataSingleton.WATCH_APP_ACTIVE

        _scope.launch {
            try {
                val task = _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
                val res = Tasks.await(task)

                // default is "none" = no phone connected
                _connectedNodeDisplayName.value = "none"
                connectedNode = null
                if (res.containsKey(deviceCap)) {
                    val phoneNodes = res.getValue(deviceCap).nodes
                    if (phoneNodes.count() > 1) {
                        throw Exception("More than one node with phone-capability detected: $phoneNodes")
                    } else if (phoneNodes.count() == 1) {
                        _connectedNodeDisplayName.value = phoneNodes.first().displayName
                        connectedNode = phoneNodes.first()
                    }
                }

                // default is false = app is not active
                _connectedActiveApp.value = false
                if (res.containsKey(appCap)) {
                    _connectedActiveApp.value = res.getValue(appCap).nodes.isNotEmpty()
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(TAG, "Querying nodes failed: $exception")
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {

        val deviceCap = DataSingleton.WATCH_CAPABILITY
        val appCap = DataSingleton.WATCH_APP_ACTIVE
        val nodes = capabilityInfo.nodes

        when (capabilityInfo.name) {
            deviceCap -> {
                if (nodes.count() > 1) {
                    throw Exception("More than one node with $deviceCap detected: $nodes")
                } else if (nodes.isEmpty()) {
                    _connectedNodeDisplayName.value = "No device"
                    connectedNode = null
                } else {
                    _connectedNodeDisplayName.value = nodes.first().displayName
                    connectedNode = nodes.first()
                }
                Log.d(TAG, "Connected device with $deviceCap : $nodes")
            }

            appCap -> {
                if (nodes.count() > 1) {
                    throw Exception("More than one node with $appCap detected: $nodes")
                } else {
                    _connectedActiveApp.value = nodes.isNotEmpty()
                }
                Log.d(TAG, "Connected device with $appCap : $nodes")
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
        _rotVec = newReadout // [x,y,z,w, confidence]
    }

    fun onGyroReadout(newReadout: FloatArray) {
        _gyro = newReadout
    }

    fun onMagnReadout(newReadout: FloatArray) {
        _magn = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }

}