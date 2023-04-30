package com.mocap.phone.viewmodel

import android.app.Application
import android.hardware.Sensor
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import com.mocap.phone.modules.SensorListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.Buffer
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime
import java.util.LinkedList

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

    private var _connectedNodeId: String = "none"
    private val _connectedNodeDisplayName = MutableStateFlow("none")
    val nodeName = _connectedNodeDisplayName.asStateFlow()

    private val _connectedActiveApp = MutableStateFlow(false)
    val appActive = _connectedActiveApp.asStateFlow()

    private val _streamState = MutableStateFlow(StreamState.Idle)
    val streamState = _streamState.asStateFlow()

    private val _watchStreamHz = MutableStateFlow(0.0F)
    val watchStreamHz = _watchStreamHz.asStateFlow()

    private val _broadcastHz = MutableStateFlow(0.0F)
    val broadcastHz = _broadcastHz.asStateFlow()

    private val _queueSize = MutableStateFlow(0)
    val queueSize = _queueSize.asStateFlow()

    // callbacks will write to these variables
    private var _rotVec: FloatArray = FloatArray(5) // Rotation Vector sensor or estimation
    private var _lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _gyro: FloatArray = FloatArray(3) // gyroscope
    private var _magn: FloatArray = FloatArray(3) // magnetic

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

    // streamed messages from the watch are stored in a queue and then processed by a separate thread
    private var _datQueue = LinkedList<ByteArray>()


    private suspend fun udpImuStream(channel: Channel) {
        // our constants for this loop
        val port = DataSingleton.UDP_IMU_PORT
        val ip = DataSingleton.ip.value
        val streamInterval = DataSingleton.UDP_IMU_INTERVAL
        val udpMessageSize = DataSingleton.UDP_IMU_MSG_SIZE

        try {
            withContext(Dispatchers.IO) {
                // open a socket
                val udpSocket = DatagramSocket(port)
                udpSocket.broadcast = true
                val socketInetAddress = InetAddress.getByName(ip)

                Log.v(TAG, "Opened UDP socket to $ip:$port")

                udpSocket.use {
                    // set the initial variables
                    var streamTs = LocalDateTime.now()
                    var count = 0

                    // begin the loop
                    while (streamState.value == StreamState.Streaming) {
                        // estimate the message frequency
                        val diff = Duration.between(streamTs, LocalDateTime.now()).toMillis()
                        if (diff >= 2000) {
                            streamTs = LocalDateTime.now()
                            _broadcastHz.value = count.toFloat() / 2.0F
                            _queueSize.value = _datQueue.count()
                            count = 0
                        }

                        // get data from queue
                        var lastDat = _datQueue.pollFirst()

//                        while (_datQueue.isNotEmpty()) {
//                            lastDat = _datQueue.pollFirst()
//                        }


                        // if we got some data from the watch...
                        if (lastDat != null) {

                            val b = ByteBuffer.wrap(lastDat)

                            // ... parse global watch rotation
                            val watchRotG = floatArrayOf(
                                b.getFloat(4), b.getFloat(8),
                                b.getFloat(12), b.getFloat(16)
                            )
                            // estimate relative rotation from calibration values
                            val watchRotR = quatDiff(DataSingleton.watchQuat.value, watchRotG)

                            // also, estimate relative phone rotation
                            val phoneRotR = quatDiff(DataSingleton.phoneQuat.value, _rotVec)
                            // compose phone data message
                            val udpData = phoneRotR + // rotation quaternion [w,x,y,z]
                                    _lacc + // linear acceleration [x,y,z]
                                    _grav + // magnitude of gravity [x,y,z]
                                    _gyro // gyro data [x,y,z]

                            // write phone and watch data to buffer
                            val buffer = ByteBuffer.allocate(4 * 8)
                            for (v in watchRotR) {
                                buffer.putFloat(v)
                            }
                            for (v in phoneRotR) {
                                buffer.putFloat(v)
                            }

                            val dp = DatagramPacket(
                                buffer.array(),
                                buffer.capacity(),
                                socketInetAddress,
                                port
                            )

                            // finally, send via UDP
                            udpSocket.send(dp)
                            count += 1
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _channelClient.close(channel)
            Log.v(TAG, "UDP Streaming error $e")
            _streamState.value = StreamState.Error
        }
    }


    private fun watchInputStream(channel: Channel) {
        // get the input stream from the opened channel
        val streamTask = _channelClient.getInputStream(channel)
        val stream = Tasks.await(streamTask)

        try {
            stream.use {
                // set the initial variables
                var streamTs = LocalDateTime.now()
                var count = 0

                // begin the loop
                while (streamState.value == StreamState.Streaming) {

                    // estimate the message frequency
                    val diff = Duration.between(streamTs, LocalDateTime.now()).toMillis()
                    if (diff >= 2000) {
                        streamTs = LocalDateTime.now()
                        _watchStreamHz.value = count.toFloat() / 2.0F
                        count = 0
                    }

                    // if more than 0bytes are available
                    if (stream.available() > 0) {
                        // read input stream message into buffer
                        val buffer = ByteBuffer.allocate(4 * DataSingleton.WATCH_MSG_SIZE)
                        stream.read(buffer.array(), 0, buffer.capacity())
                        _datQueue.add(buffer.array())
                        count += 1
                    }
                }
            }
        } catch (e: CancellationException) {
            // In case the scope gets cancelled while still in the loop
            _channelClient.close(channel)
            Log.d(TAG, "Unexpected scope cancel \n" + e.message.toString())
            _streamState.value = StreamState.Error
        } catch (e: Exception) {
            _channelClient.close(channel)
            Log.v(TAG, e.message.toString())
            _streamState.value = StreamState.Error
        }
    }

    override fun onCleared() {
        _scope.cancel()
        _streamState.value = StreamState.Idle
        Log.d(TAG, "Cleared")
    }

    fun onWatchChannelOpen(channel: Channel) {
        Log.d(TAG, "Channel opened by ${channel.nodeId}")
        // set the local state to Streaming and start two loops
        _streamState.value = StreamState.Streaming
        // First, start the coroutine to fill the queue with streamed watch data
        _scope.launch { watchInputStream(channel) }
        // Then, start the coroutine to deal with queued data and broadcast it via UDP
        _scope.launch { udpImuStream(channel) }
    }

    fun onWatchChannelClose(c: Channel) {
        _streamState.value = StreamState.Idle
    }

    fun queryCapabilities() {
        _scope.launch {
            try {
                val task =
                    _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
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
        val deviceCap = DataSingleton.WATCH_CAPABILITY
        val appCap = DataSingleton.WATCH_APP_ACTIVE
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

    fun onGyroReadout(newReadout: FloatArray) {
        _gyro = newReadout
    }

    fun onMagnReadout(newReadout: FloatArray) {
        _magn = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }

    private fun quatDiff(a: FloatArray, b: FloatArray): FloatArray {
        // get the conjugate
        val aI = floatArrayOf(a[0], -a[1], -a[2], -a[3])
        // Hamilton product as H(A,B)
        val hab = floatArrayOf(
            aI[0] * b[0] - aI[1] * b[1] - aI[2] * b[2] - aI[3] * b[3],
            aI[0] * b[1] + aI[1] * b[0] + aI[2] * b[3] - aI[3] * b[2],
            aI[0] * b[2] - aI[1] * b[3] + aI[2] * b[0] + aI[3] * b[1],
            aI[0] * b[3] + aI[1] * b[2] - aI[2] * b[1] + aI[3] * b[0]
        )
        return hab
    }

}