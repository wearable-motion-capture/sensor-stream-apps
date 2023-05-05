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
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer


class AudioService : Service() {

    companion object {
        private const val TAG = "Audio Service"  // for logging
        private const val AUDIO_RATE = 16000 // can go up to 44K, if needed
        private const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private var _audioStreamState = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun streamTrigger(nodeId: String) {
        if (_audioStreamState) {
            Log.w(TAG, "stream already started")
            stopSelf()
            return
        }
        _scope.launch {
            try {
                // Open the channel
                val channel = _channelClient.openChannel(
                    nodeId,
                    DataSingleton.AUDIO_CHANNEL_PATH
                ).await()
                Log.d(TAG, "Opened ${DataSingleton.IMU_CHANNEL_PATH} to $nodeId")

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
                    .setBufferSizeInBytes(DataSingleton.AUDIO_BUFFER_SIZE)
                    .build()
                // begin streaming the microphone
                audioRecord.startRecording()

                try {
                    // get output stream
                    val outputStream = _channelClient.getOutputStream(channel).await()
                    outputStream.use {
                        // start the stream loop
                        _audioStreamState = true
                        while (_audioStreamState) {
                            val buffer = ByteBuffer.allocate(DataSingleton.AUDIO_BUFFER_SIZE)
                            audioRecord.read(buffer.array(), 0, buffer.capacity())
                            outputStream.write(buffer.array(), 0, buffer.capacity())
                        }
                    }
                } catch (e: Exception) {
                    // In case the channel gets destroyed while still in the loop
                    Log.d(TAG, e.message.toString())
                } finally {
                    _audioStreamState = false
                    Log.d(TAG, "Audio stream stopped")
                    audioRecord.release()
                    _channelClient.close(channel)
                    stopSelf() // stop service
                }
            } catch (e: Exception) {
                // In case the channel gets destroyed while still in the loop
                Log.w(TAG, "Channel failed" + e.message.toString())
                stopSelf() // stop service
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        val sourceId = intent.extras?.getString("sourceNodeId")
        if (sourceId == null) {
            Log.w(TAG, "no Node ID given")
            stopSelf()
            return START_NOT_STICKY
        }
        streamTrigger(sourceId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _audioStreamState = false
        _scope.cancel()
        val intent = Intent(DataSingleton.BROADCAST_CLOSE)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.AUDIO_CHANNEL_PATH)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    /** not intended to be bound **/
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}