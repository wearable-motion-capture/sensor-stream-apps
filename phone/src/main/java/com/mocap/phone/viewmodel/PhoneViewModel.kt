package com.mocap.phone.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import com.mocap.phone.PpgStreamState
import com.mocap.phone.SoundStreamState
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

//TODO: Move broadcast and streaming into Services as in Dual-Mode app
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
    private var _ppgInCount: Int = 0
    private var _ppgOutCount: Int = 0
    private var _audioCount: Int = 0

    // UI elements to inform the user about the internal state
    private val _connectedNodeDisplayName = MutableStateFlow("none")
    val nodeName = _connectedNodeDisplayName.asStateFlow()

    private val _pingSuccess = MutableStateFlow(false)
    val appActive = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    private val _audioStreamState = MutableStateFlow(SoundStreamState.Idle)
    val audioStreamState = _audioStreamState.asStateFlow()

    private val _audioBroadcastHz = MutableStateFlow(0.0F)
    val audioBroadcastHz = _audioBroadcastHz.asStateFlow()

    private val _audioStreamQueue = MutableStateFlow(0)
    val audioStreamQueue = _audioStreamQueue.asStateFlow()

    private val _ppgStreamState = MutableStateFlow(PpgStreamState.Idle)
    val ppgStreamState = _ppgStreamState.asStateFlow()

    private val _imuStreamState = MutableStateFlow(false)
    val imuStreamState = _imuStreamState.asStateFlow()

    private val _imuInHz = MutableStateFlow(0.0F)
    val imuInHz = _imuInHz.asStateFlow()

    private val _imuOutHz = MutableStateFlow(0.0F)
    val imuOutHz = _imuOutHz.asStateFlow()

    private val _imuQueueSize = MutableStateFlow(0)
    val imuQueueSize = _imuQueueSize.asStateFlow()

    private val _ppgInHz = MutableStateFlow(0.0F)
    val ppgInHz = _ppgInHz.asStateFlow()

    private val _ppgOutHz = MutableStateFlow(0.0F)
    val ppgOutHz = _ppgOutHz.asStateFlow()

    private val _ppgQueueSize = MutableStateFlow(0)
    val ppgQueueSize = _ppgQueueSize.asStateFlow()

    // two distinct threads fill and process streamed messages from the watch via this queue
    private var _ppgQueue = ConcurrentLinkedQueue<ByteArray>()

    fun onServiceUpdate(intent: Intent) {
        when (intent.getStringExtra(DataSingleton.BROADCAST_SERVICE_KEY)) {
            DataSingleton.IMU_CHANNEL_PATH -> {
                _imuInHz.value = intent.getFloatExtra(
                    DataSingleton.BROADCAST_SERVICE_HZ_IN, 0.0F
                )
                _imuOutHz.value = intent.getFloatExtra(
                    DataSingleton.BROADCAST_SERVICE_HZ_OUT, 0.0F
                )
                _imuQueueSize.value = intent.getIntExtra(
                    DataSingleton.BROADCAST_SERVICE_QUEUE, 0
                )
                _imuStreamState.value = intent.getBooleanExtra(
                    DataSingleton.BROADCAST_SERVICE_STATE, false
                )
            }
        }
    }

    /**
     * update display frequencies on the screen
     */
    fun regularUiUpdates() {
        _scope.launch {
            while (true) {
                _ppgOutHz.value = _ppgOutCount.toFloat() / 2.0F
                _ppgInHz.value = _ppgInCount.toFloat() / 2.0F
                _audioBroadcastHz.value = _audioCount.toFloat() / 2.0F
                _ppgQueueSize.value = _ppgQueue.count()
                _ppgOutCount = 0
                _ppgInCount = 0
                _audioCount = 0
                requestPing() // confirm both apps are active
                delay(2000L)
            }
        }
    }

    /**
     * reads watch IMU messages from _imuPpgQueue and broadcasts them via UDP
     */
    private suspend fun udpPpgBroadcast(c: Channel) {
        try {
            // our constants for this loop
            val port = DataSingleton.UDP_PPG_PORT
            val ip = DataSingleton.ip.value

            withContext(Dispatchers.IO) {
                // open a socket
                val udpSocket = DatagramSocket(port)
                udpSocket.broadcast = true
                val socketInetAddress = InetAddress.getByName(ip)
                Log.v(TAG, "Opened UDP socket to $ip:$port")

                udpSocket.use {
                    // begin the loop
                    while (ppgStreamState.value == PpgStreamState.Streaming) {

                        // get data from queue
                        // skip entries that are already old
                        var lastDat = _ppgQueue.poll()
                        while (_ppgQueue.count() > 10) {
                            lastDat = _ppgQueue.poll()
                        }

                        // if we got some data from the watch...
                        if (lastDat != null) {
                            // write phone and watch data to buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.PPG_MSG_SIZE)
                            // put smartwatch data
                            buffer.put(lastDat)

                            // create packet
                            val dp = DatagramPacket(
                                buffer.array(),
                                buffer.capacity(),
                                socketInetAddress,
                                port
                            )
                            // finally, send via UDP
                            udpSocket.send(dp)
                            _ppgOutCount += 1 // for Hz estimation
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e.message.toString())
            _channelClient.close(c)
        }
    }

    private fun ppgQueueFiller(c: Channel) {
        try {
            // get the input stream from the opened channel
            val streamTask = _channelClient.getInputStream(c)
            val stream = Tasks.await(streamTask)
            stream.use {
                // begin the loop
                while (ppgStreamState.value == PpgStreamState.Streaming) {
                    // if more than 0 bytes are available
                    if (stream.available() > 0) {
                        // read input stream message into buffer
                        val buffer = ByteBuffer.allocate(DataSingleton.PPG_MSG_SIZE)
                        stream.read(buffer.array(), 0, buffer.capacity())
                        _ppgQueue.add(buffer.array())
                        // for Hz estimation
                        _ppgInCount += 1
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e.message.toString())
            _ppgStreamState.value = PpgStreamState.Error
            _channelClient.close(c)
        }
    }

    private fun onPpgChannelOpened(c: Channel) {
        // set the local state to Streaming and start three loops
        _ppgStreamState.value = PpgStreamState.Streaming
        // First, start the coroutine to fill the queue with streamed watch data
        _scope.launch { ppgQueueFiller(c) }
        // Also, start the coroutine to deal with queued data and broadcast it via UDP
        _scope.launch { udpPpgBroadcast(c) }
    }

    private fun onSoundChannelOpened(c: Channel) {
        _scope.launch {
            // our constants for this loop
            val port = DataSingleton.UDP_AUDIO_PORT
            val ip = DataSingleton.ip.value

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
                            _audioStreamState.value = SoundStreamState.Streaming
                            while (audioStreamState.value == SoundStreamState.Streaming) {
                                // read input stream message into buffer
                                if (stream.available() > 0) {
                                    val buffer =
                                        ByteBuffer.allocate(DataSingleton.AUDIO_BUFFER_SIZE)
                                    stream.read(buffer.array(), 0, buffer.capacity())
                                    // create packet
                                    val dp = DatagramPacket(
                                        buffer.array(), buffer.capacity(),
                                        socketInetAddress, port
                                    )
                                    // broadcast via UDP
                                    udpSocket.send(dp)
                                    _audioCount += 1 // for Hz estimation
                                    _audioStreamQueue.value =
                                        stream.available() // inform about stream overhead
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, e.message.toString())
                    _audioStreamState.value = SoundStreamState.Error
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
            try {
                _messageClient.sendMessage(_connectedNodeId, DataSingleton.PING_REQ, null).await()
                delay(1000L)
                // reset success indicator if the response takes too long
                if (Duration.between(_lastPing, LocalDateTime.now()).toMillis() > 1100L) {
                    _pingSuccess.value = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ping message to nodeID: $_connectedNodeId failed")
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

    fun resetStreamStates() {
        _audioStreamState.value = SoundStreamState.Idle
        _ppgStreamState.value = PpgStreamState.Idle
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
            DataSingleton.AUDIO_CHANNEL_PATH -> onSoundChannelOpened(c = channel)
            DataSingleton.PPG_CHANNEL_PATH -> onPpgChannelOpened(c = channel)
        }
    }

    fun onChannelClose(channel: Channel) {
        when (channel.path) {
            DataSingleton.AUDIO_CHANNEL_PATH -> _audioStreamState.value =
                when (_audioStreamState.value) {
                    SoundStreamState.Idle -> SoundStreamState.Idle
                    SoundStreamState.Streaming -> SoundStreamState.Idle
                    SoundStreamState.Error -> SoundStreamState.Error
                }

            DataSingleton.PPG_CHANNEL_PATH -> _ppgStreamState.value =
                when (_ppgStreamState.value) {
                    PpgStreamState.Idle -> PpgStreamState.Idle
                    PpgStreamState.Streaming -> PpgStreamState.Idle
                    PpgStreamState.Error -> PpgStreamState.Error
                }
        }
    }
}