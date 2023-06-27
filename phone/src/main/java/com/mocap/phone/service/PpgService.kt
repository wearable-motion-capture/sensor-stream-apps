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
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.round

class PpgService : Service() {

    companion object {
        private const val TAG = "Channel PPG Service"  // for logging
        private const val MS2S = 0.001f
    }

    /**
     * This service is dormant until Channels from the watch are opened. This callback triggers
     * when Channel states change.
     */
    private val _channelCallback = PhoneChannelCallback(
        openCallback = { onChannelOpen(it) },
        closeCallback = { onChannelClose(it) }
    )

    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private var _lastBroadcast = LocalDateTime.now()
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    // two distinct threads fill and process streamed messages from the watch via this queue
    private var _ppgQueue = ConcurrentLinkedQueue<ByteArray>()
    private var _ppgInCount: Int = 0
    private var _ppgOutCount: Int = 0
    private var _ppgStreamState: Boolean = false

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
            DataSingleton.PPG_PATH
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_STATE,
            _ppgStreamState
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_HZ_IN,
            round(_ppgInCount.toFloat() / ds)
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_HZ_OUT,
            round(_ppgOutCount.toFloat() / ds)
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_QUEUE,
            _ppgQueue.count()
        )
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        _ppgInCount = 0
        _ppgOutCount = 0
    }

    /**
     * reads watch IMU messages from _imuPpgQueue and broadcasts them via UDP
     */
    private suspend fun sendPpgMessages(c: ChannelClient.Channel) {
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
                    while (_ppgStreamState) {

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
            Log.w(TAG, e)
            _channelClient.close(c)
            onChannelClose(c) // make sure the callback is triggered,
            // the exception might kill it beforehand
        }
    }

    private fun ppgQueueFiller(c: ChannelClient.Channel) {
        try {
            // get the input stream from the opened channel
            val streamTask = _channelClient.getInputStream(c)
            val stream = Tasks.await(streamTask)
            stream.use {
                // begin the loop
                while (_ppgStreamState) {
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
            Log.w(TAG, e)
        } finally {
            _ppgQueue.clear()
            _channelClient.close(c)
            onChannelClose(c) // make sure the callback is triggered,
            // the exception might kill it beforehand
        }
    }

    private fun onChannelOpen(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.PPG_PATH) {
            // set the local state to Streaming and start three loops
            _ppgStreamState = true
            // First, start the coroutine to fill the queue with streamed watch data
            _scope.launch { ppgQueueFiller(c) }
            // Also, start the coroutine to deal with queued data and broadcast it via UDP
            _scope.launch { sendPpgMessages(c) }
            broadcastUiUpdate()
        }
    }

    private fun onChannelClose(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.PPG_PATH) {
            _ppgStreamState = false
            broadcastUiUpdate()
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        // not intended to be bound
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        _ppgStreamState = false
        _scope.cancel()
        _channelClient.unregisterChannelCallback(_channelCallback)
        Log.v(TAG, "Service destroyed")
    }
}