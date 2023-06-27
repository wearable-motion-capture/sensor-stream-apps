package com.mocap.phone.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import com.mocap.phone.modules.PhoneChannelCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.round

class AudioService : Service() {

    companion object {
        private const val TAG = "Channel Audio Service"  // for logging
        private const val MS2S = 0.001f
    }

    /**
     * This service is dormant until Channels from the watch are opened. This callback triggers
     * when Channel states change.
     */
    private val _channelCallback = PhoneChannelCallback(
        openCallback = { onSoundChannelOpened(it) },
        closeCallback = { onChannelClose(it) }
    )

    private var _audioStreamState = false
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private var _audioStreamQueue: Int = 0
    private var _audioCount: Int = 0
    private var _lastBroadcast = LocalDateTime.now()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _channelClient.registerChannelCallback(_channelCallback)
        _scope.launch {
            while (true) {
                broadcastUiUpdate()
                delay(2000L)
            }
        }
        Log.v(TAG, "Service started")
        return START_NOT_STICKY
    }

    /** Broadcasts service status values to view model to update the UI */
    private fun broadcastUiUpdate() {
        // duration estimate in seconds for Hz
        val now = LocalDateTime.now()
        val diff = Duration.between(_lastBroadcast, now)
        val ds = diff.toMillis() * MS2S
        _lastBroadcast = LocalDateTime.now()

        val intent = Intent(DataSingleton.BROADCAST_UPDATE)
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_KEY,
            DataSingleton.AUDIO_CHANNEL_PATH
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_STATE,
            _audioStreamState
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_HZ_IN,
            round(_audioCount.toFloat() / ds)
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_QUEUE,
            _audioStreamQueue
        )
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        _audioCount = 0
    }

    private fun onSoundChannelOpened(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.AUDIO_CHANNEL_PATH) {
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

                        // now, start the stream and trigger an immediate UI update
                        _audioStreamState = true
                        broadcastUiUpdate()

                        stream.use {
                            udpSocket.use {
                                // begin the loop
                                while (_audioStreamState) {
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
                                        // inform about stream overhead
                                        _audioStreamQueue = stream.available()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, e.message.toString())
                    } finally {
                        _channelClient.close(c)
                        onChannelClose(c) // make sure the callback is triggered,
                        // the exception might kill it beforehand
                    }
                }
            }
        }
    }

    private fun onChannelClose(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.AUDIO_CHANNEL_PATH) {
            _audioStreamState = false
            broadcastUiUpdate()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // not intended to be bound
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        _audioStreamState = false
        _scope.cancel()
        _channelClient.unregisterChannelCallback(_channelCallback)
        Log.v(TAG, "Service destroyed")
    }
}