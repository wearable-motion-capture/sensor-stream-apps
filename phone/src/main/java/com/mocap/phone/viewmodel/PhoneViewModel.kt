package com.mocap.phone.viewmodel

import android.app.Application
import android.hardware.Sensor
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import com.mocap.phone.ImuPpgStreamState
import com.mocap.phone.SoundStreamState
import com.mocap.phone.modules.SensorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue

class PhoneViewModel(application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "PhoneViewModel"
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private var _connectedNodeId: String = "none"
    private var _watchMsgCount: Int = 0
    private var _imuPpgBroadcastCount: Int = 0
    private var _soundBroadcastCount: Int = 0

    // UI elements to inform the user about the internal state
    private val _connectedNodeDisplayName = MutableStateFlow("none")
    val nodeName = _connectedNodeDisplayName.asStateFlow()

    private val _pingSuccess = MutableStateFlow(false)
    val appActive = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    private val _soundStreamState = MutableStateFlow(SoundStreamState.Idle)
    val soundStreamState = _soundStreamState.asStateFlow()

    private val _soundBroadcastHz = MutableStateFlow(0.0F)
    val soundBroadcastHz = _soundBroadcastHz.asStateFlow()

    private val _soundStreamQueue = MutableStateFlow(0)
    val soundStreamQueue = _soundStreamQueue.asStateFlow()

    private val _imuPpgStreamState = MutableStateFlow(ImuPpgStreamState.Idle)
    val imuPpgStreamState = _imuPpgStreamState.asStateFlow()

    private val _imuPpgStreamHz = MutableStateFlow(0.0F)
    val imuPpgStreamHz = _imuPpgStreamHz.asStateFlow()

    private val _imuPpgBroadcastHz = MutableStateFlow(0.0F)
    val imuPpgBroadcastHz = _imuPpgBroadcastHz.asStateFlow()

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

    // two distinct threads fill and process streamed messages from the watch via this queue
    private var _imuPpgQueue = ConcurrentLinkedQueue<ByteArray>()

    /**
     * update display frequencies on the screen
     */
    fun regularUiUpdates() {
        _scope.launch {
            while (true) {
                _imuPpgBroadcastHz.value = _imuPpgBroadcastCount.toFloat() / 2.0F
                _imuPpgStreamHz.value = _watchMsgCount.toFloat() / 2.0F
                _soundBroadcastHz.value = _soundBroadcastCount.toFloat() / 2.0F
                _queueSize.value = _imuPpgQueue.count()
                _imuPpgBroadcastCount = 0
                _watchMsgCount = 0
                _soundBroadcastCount = 0
                requestPing() // confirm both apps are active
                delay(2000L)
            }
        }
    }

    /**
     * reads watch IMU + PPG messages from _imuPpgQueue and broadcasts them via UDP
     */
    private suspend fun udpImuPpgBroadcast(c: Channel) {
        try {
            // our constants for this loop
            val port = DataSingleton.UDP_IMU_PORT
            val ip = DataSingleton.ip.value
            val udpMsgSize = DataSingleton.UDP_IMU_MSG_SIZE
            val calibratedDat = DataSingleton.watchQuat.value +
                    DataSingleton.phoneQuat.value +
                    floatArrayOf(DataSingleton.watchPres.value)

            withContext(Dispatchers.IO) {
                // open a socket
                val udpSocket = DatagramSocket(port)
                udpSocket.broadcast = true
                val socketInetAddress = InetAddress.getByName(ip)
                Log.v(TAG, "Opened UDP socket to $ip:$port")

                udpSocket.use {
                    // begin the loop
                    while (imuPpgStreamState.value == ImuPpgStreamState.Streaming) {

                        // get data from queue
                        // skip entries that are already old
                        var lastDat = _imuPpgQueue.poll()
                        while (_imuPpgQueue.isNotEmpty()) {
                            lastDat = _imuPpgQueue.poll()
                        }

                        // if we got some data from the watch...
                        if (lastDat != null) {

                            // compose phone message data
                            val udpData = _rotVec + // rotation quaternion [w,x,y,z]
                                    _lacc + // linear acceleration [x,y,z]
                                    _grav + // magnitude of gravity [x,y,z]
                                    _gyro + // gyro data [x,y,z]
                                    calibratedDat // from calibration

                            // write phone and watch data to buffer
                            val buffer = ByteBuffer.allocate(4 * udpMsgSize)
                            // put smartwatch data
                            buffer.put(lastDat)
                            // append phone data
                            for (v in udpData) {
                                buffer.putFloat(v)
                            }

                            // create packet
                            val dp = DatagramPacket(
                                buffer.array(),
                                buffer.capacity(),
                                socketInetAddress,
                                port
                            )
                            // finally, send via UDP
                            udpSocket.send(dp)
                            _imuPpgBroadcastCount += 1 // for Hz estimation
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e.message.toString())
            _imuPpgStreamState.value = ImuPpgStreamState.Error
            _channelClient.close(c)
        }
    }

    /**
     * Fills the _imuPpgQueue
     */
    private fun imuPpgQueueFiller(c: Channel) {
        try {
            // get the input stream from the opened channel
            val streamTask = _channelClient.getInputStream(c)
            val stream = Tasks.await(streamTask)
            stream.use {
                // begin the loop
                while (imuPpgStreamState.value == ImuPpgStreamState.Streaming) {
                    // if more than 0 bytes are available
                    if (stream.available() > 0) {
                        // read input stream message into buffer
                        val buffer = ByteBuffer.allocate(4 * DataSingleton.WATCH_MSG_SIZE)
                        stream.read(buffer.array(), 0, buffer.capacity())
                        _imuPpgQueue.add(buffer.array())
                        // for Hz estimation
                        _watchMsgCount += 1
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e.message.toString())
            _imuPpgStreamState.value = ImuPpgStreamState.Error
            _channelClient.close(c)
        }
    }

    private fun onImuPpgChannelOpened(c: Channel) {
        // set the local state to Streaming and start three loops
        _imuPpgStreamState.value = ImuPpgStreamState.Streaming
        // First, start the coroutine to fill the queue with streamed watch data
        _scope.launch { imuPpgQueueFiller(c) }
        // Also, start the coroutine to deal with queued data and broadcast it via UDP
        _scope.launch { udpImuPpgBroadcast(c) }
    }

    private fun onSoundChannelOpened(c: Channel) {
        _scope.launch {
            // our constants for this loop
            val port = DataSingleton.UDP_AUDIO_PORT
            val ip = DataSingleton.ip.value
            val msgSize = DataSingleton.AUDIO_BUFFER_SIZE

            withContext(Dispatchers.IO) {
                try {
                    // open a socket
                    val udpSocket = DatagramSocket(port)
                    udpSocket.broadcast = true
                    val socketInetAddress = InetAddress.getByName(ip)
                    Log.v(TAG, "Opened UDP socket to $ip:$port")

                    // get input stream
                    val streamTask = _channelClient.getInputStream(c)
                    val stream = Tasks.await(streamTask)

                    stream.use {
                        udpSocket.use {
                            // begin the loop
                            _soundStreamState.value = SoundStreamState.Streaming
                            while (soundStreamState.value == SoundStreamState.Streaming) {
                                // read input stream message into buffer
                                if (stream.available() > 0) {
                                    val buffer = ByteBuffer.allocate(msgSize)
                                    stream.read(buffer.array(), 0, buffer.capacity())
                                    // create packet
                                    val dp = DatagramPacket(
                                        buffer.array(), buffer.capacity(),
                                        socketInetAddress, port
                                    )
                                    // broadcast via UDP
                                    udpSocket.send(dp)
                                    _soundBroadcastCount += 1 // for Hz estimation
                                    _soundStreamQueue.value =
                                        stream.available() // inform about stream overhead
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, e.message.toString())
                    _soundStreamState.value = SoundStreamState.Error
                } finally {
                    _channelClient.close(c)
                }
            }
        }
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

    fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val deviceCap = DataSingleton.WATCH_CAPABILITY
        // this checks if a phone is available at all
        when (capabilityInfo.name) {
            deviceCap -> {
                val nodes = capabilityInfo.nodes
                if (nodes.count() > 1) {
                    throw Exception("More than one node with $deviceCap detected: $nodes")
                } else if (nodes.isEmpty()) {
                    _connectedNodeDisplayName.value = "No device"
                    _connectedNodeId = "none"
                } else {
                    _connectedNodeDisplayName.value = nodes.first().displayName
                    _connectedNodeId = nodes.first().id
                }
                Log.d(TAG, "Connected phone : $nodes")
            }
        }
        requestPing() // check if the app is answering to pings
    }

    /** send a ping request */
    private fun requestPing() {
        _scope.launch {
            _messageClient.sendMessage(_connectedNodeId, DataSingleton.PING_REQ, null).await()
            delay(1000L)
            // reset success indicator if the response takes too long
            if (Duration.between(_lastPing, LocalDateTime.now()).toMillis() > 1100L) {
                _pingSuccess.value = false
            }
        }
    }

    /**
     * check for ping messages
     * This function is called by the listener registered in the PhoneMain Activity
     */
    fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            DataSingleton.PING_REP -> {
                _pingSuccess.value = true
                _lastPing = LocalDateTime.now()
            }
            DataSingleton.PING_REQ -> {
                // reply to a ping request
                _scope.launch {
                    _messageClient.sendMessage(_connectedNodeId, DataSingleton.PING_REP, null)
                        .await()
                }
            }
        }
    }

    // Events
    /** sensor callbacks */
    // Individual sensor reads are triggered by their onValueChanged events
    private fun onLaccReadout(newReadout: FloatArray) {
        _lacc = newReadout
    }

    private fun onRotVecReadout(newReadout: FloatArray) {
        // newReadout is [x,y,z,w, confidence]
        // our preferred order system is [w,x,y,z]
        _rotVec = floatArrayOf(
            newReadout[3],
            newReadout[0],
            newReadout[1],
            newReadout[2]
        )
    }

    private fun onGyroReadout(newReadout: FloatArray) {
        _gyro = newReadout
    }

    private fun onMagnReadout(newReadout: FloatArray) {
        _magn = newReadout
    }

    private fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }

    fun resetStreamStates() {
        _imuPpgStreamState.value = ImuPpgStreamState.Idle
        _soundStreamState.value = SoundStreamState.Idle
    }

    /** Other callbacks */
    override fun onCleared() {
        super.onCleared()
        resetStreamStates()
        _scope.cancel()
        Log.d(TAG, "Cleared")
    }

    fun onChannelOpen(channel: Channel) {
        when (channel.path) {
            DataSingleton.SENSOR_CHANNEL_PATH -> onImuPpgChannelOpened(c = channel)
            DataSingleton.SOUND_CHANNEL_PATH -> onSoundChannelOpened(c = channel)
        }
    }

    fun onChannelClose(channel: Channel) {
        when (channel.path) {
            DataSingleton.SENSOR_CHANNEL_PATH -> _imuPpgStreamState.value =
                when (_imuPpgStreamState.value) {
                    ImuPpgStreamState.Idle -> ImuPpgStreamState.Idle
                    ImuPpgStreamState.Streaming -> ImuPpgStreamState.Idle
                    ImuPpgStreamState.Error -> ImuPpgStreamState.Error
                }

            DataSingleton.SOUND_CHANNEL_PATH -> _soundStreamState.value =
                when (_soundStreamState.value) {
                    SoundStreamState.Idle -> SoundStreamState.Idle
                    SoundStreamState.Streaming -> SoundStreamState.Idle
                    SoundStreamState.Error -> SoundStreamState.Error
                }
        }
    }

    //    private fun quatDiff(a: FloatArray, b: FloatArray): FloatArray {
//        // get the conjugate
//        val aI = floatArrayOf(a[0], -a[1], -a[2], -a[3])
//        // Hamilton product as H(A,B)
//        val hab = floatArrayOf(
//            aI[0] * b[0] - aI[1] * b[1] - aI[2] * b[2] - aI[3] * b[3],
//            aI[0] * b[1] + aI[1] * b[0] + aI[2] * b[3] - aI[3] * b[2],
//            aI[0] * b[2] - aI[1] * b[3] + aI[2] * b[0] + aI[3] * b[1],
//            aI[0] * b[3] + aI[1] * b[2] - aI[2] * b[1] + aI[3] * b[0]
//        )
//        return hab
//    }
}