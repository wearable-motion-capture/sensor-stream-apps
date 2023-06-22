package com.mocap.watch.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer


class UdpAudioService : Service() {

    companion object {
        private const val TAG = "Audio Service"  // for logging
        private const val AUDIO_RATE = 16000 // can go up to 44K, if needed
        private const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BUFFER_SIZE = 1600
    }

    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private var _audioStreamState = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun streamTrigger() {
        if (_audioStreamState) {
            Log.w(TAG, "stream already started")
            onDestroy() // stop service
            return
        }
        _scope.launch {
            val ip = DataSingleton.IP.value
            val port = DataSingleton.UDP_AUDIO_PORT

            // run the streaming in a thread
            withContext(Dispatchers.IO) {
                // Create an AudioRecord object for the streaming
                val audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(AUDIO_RATE)
                            .setChannelMask(AUDIO_CHANNEL_IN)
                            .setEncoding(AUDIO_FORMAT)
                            .build()
                    )
                    .setBufferSizeInBytes(AUDIO_BUFFER_SIZE)
                    .build()

                // begin streaming the microphone
                audioRecord.startRecording()
                Log.w(TAG, "Started Recording")

                try {
                    // open a socket
                    val udpSocket = DatagramSocket(port)
                    udpSocket.broadcast = true
                    val socketInetAddress = InetAddress.getByName(ip)
                    Log.v(TAG, "Opened UDP socket to $ip:$port")

                    udpSocket.use {
                        // read from audioRecord stream and send through to UDP output stream
                        _audioStreamState = true
                        while (_audioStreamState) {
                            val buffer = ByteBuffer.allocate(AUDIO_BUFFER_SIZE)
                            audioRecord.read(buffer.array(), 0, buffer.capacity())
                            val dp = DatagramPacket(
                                buffer.array(),
                                buffer.capacity(),
                                socketInetAddress,
                                port
                            )
                            udpSocket.send(dp)
                        }
                    }
                } catch (e: Exception) {
                    // if the loop ends of was disrupted, close everything and reset
                    Log.w(TAG, e.message.toString())
                } finally {
                    _audioStreamState = false
                    Log.d(TAG, "Audio stream stopped")
                    audioRecord.release()
                    onDestroy() // stop service
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        streamTrigger()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _audioStreamState = false
        _scope.cancel()
        val intent = Intent(DataSingleton.BROADCAST_CLOSE)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.AUDIO_UDP_PATH)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    /** not intended to be bound **/
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}