package com.mocap.watch.service.udp

import android.Manifest
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.mocap.watch.DataSingleton
import com.mocap.watch.service.BaseAudioService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer


class UdpAudioService : BaseAudioService() {

    companion object {
        private const val TAG = "UDP Audio Service"  // for logging
    }


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        if (audioStreamState) {
            Log.w(TAG, "stream already started")
            onDestroy() // stop service
        } else {
            audioStreamState = true
            scope.launch { susStreamTrigger() }
        }
        return START_NOT_STICKY
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun susStreamTrigger() {

        val ip = DataSingleton.ip.value
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
            Log.d(TAG, "Started Recording")

            try {
                // open a socket
                val udpSocket = DatagramSocket(port)
                udpSocket.broadcast = true
                val socketInetAddress = InetAddress.getByName(ip)
                Log.d(TAG, "Opened UDP socket to $ip:$port")

                udpSocket.use {
                    // read from audioRecord stream and send through to UDP output stream
                    while (audioStreamState) {
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
                Log.w(TAG, e)
            } finally {
                Log.d(TAG, "Audio stream stopped")
                audioRecord.release()
                stopService()
                onDestroy()
            }
        }
    }
}