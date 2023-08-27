package com.mocap.watch.service.channel

import android.Manifest
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.service.BaseAudioService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer


class ChannelAudioService : BaseAudioService() {

    companion object {
        private const val TAG = "Channel Audio Service"  // for logging
    }

    private val _channelClient by lazy { Wearable.getChannelClient(application) }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        val sourceId = intent.extras?.getString("sourceNodeId")
        if (sourceId == null) {
            Log.w(TAG, "no Node ID given")
            onDestroy()
        } else if (audioStreamState) {
            Log.w(TAG, "stream already started")
            onDestroy()
        } else {
            audioStreamState = true
            scope.launch { susStreamTrigger(sourceId) }
        }
        return START_NOT_STICKY
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun susStreamTrigger(nodeId: String) {
        try {
            // Open the channel
            val channel = _channelClient.openChannel(
                nodeId,
                DataSingleton.AUDIO_PATH
            ).await()
            Log.d(TAG, "Opened ${DataSingleton.AUDIO_PATH} to $nodeId")

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
                    while (audioStreamState) {
                        val buffer = ByteBuffer.allocate(DataSingleton.AUDIO_BUFFER_SIZE)
                        audioRecord.read(buffer.array(), 0, buffer.capacity())
                        outputStream.write(buffer.array(), 0, buffer.capacity())
                    }
                }
            } catch (e: Exception) {
                // In case the channel gets destroyed while still in the loop
                Log.w(TAG, e)
            } finally {
                Log.d(TAG, "Audio stream stopped")
                audioRecord.release()
                _channelClient.close(channel)
            }
        } catch (e: Exception) {
            // In case the channel gets destroyed while still in the loop
            Log.w(TAG, e)
        } finally {
            audioStreamState = false
            this.stopService()
        }
    }
}
