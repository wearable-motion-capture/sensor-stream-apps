package com.example.sensorrecord.presentation.modules

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread


/**
 * A helper class to provide methods to record audio input from the MIC
 */
class SoundStreamer(globalState: GlobalState) {

    /** setup-specific parameters */
    companion object {
        private const val TAG = "SoundRecorder" // for logging
        private const val RECORDING_RATE = 16000 // can go up to 44K, if needed
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 1600
        private const val PORT = 50001
    }

    val _gs = globalState

    /**
     * Records from the microphone.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun triggerMicStream(checked : Boolean) {
        if (!checked) {
            _gs.setSoundState(SoundStreamState.Idle)
        } else {
            // Create an AudioRecord object for the streaming
            // val intSize = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL_IN, FORMAT)
            val audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RECORDING_RATE)
                        .setChannelMask(CHANNEL_IN)
                        .setEncoding(FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(BUFFER_SIZE)
                .build()

//            // add noise suppression and echo cancellation
//            if (NoiseSuppressor.isAvailable()) {
//                NoiseSuppressor.create(audioRecord.getAudioSessionId()).setEnabled(true)
//            }
//            if (AcousticEchoCanceler.isAvailable()) {
//                AcousticEchoCanceler.create(audioRecord.getAudioSessionId()).setEnabled(true)
//            }

            // begin streaming the microphone
            audioRecord.startRecording()
            Log.w(TAG, "Started Recording")
            _gs.setSoundState(SoundStreamState.Streaming)
            // start while loop in a thread
            thread {
                try {

                    val udpSocket = DatagramSocket(PORT)
                    udpSocket.broadcast = true
                    Log.v(TAG, "Beginning to broadcast UDP mesage to ${_gs.getIP()}:$PORT")

                    // read from audioRecord stream and send through to TCP output stream
                    val buffer = ByteArray(BUFFER_SIZE)
                    val socketInetAddress = InetAddress.getByName(_gs.getIP())
                    while (_gs.getSoundState() == SoundStreamState.Streaming) {
                        audioRecord.read(buffer, 0, buffer.size)
                        val dp = DatagramPacket(
                            buffer,
                            buffer.size,
                            socketInetAddress,
                            PORT
                        )
                        udpSocket.send(dp)
                    }
                    // close connection when done
                    udpSocket.close()

                } catch (e: Exception) {
                    Log.v(TAG, "Streaming error $e")
                    _gs.setSoundState(SoundStreamState.Idle)
                    Log.v(TAG, "stopped streaming")
                } finally {
                    audioRecord.release()
                }
            }
        }
    }
}