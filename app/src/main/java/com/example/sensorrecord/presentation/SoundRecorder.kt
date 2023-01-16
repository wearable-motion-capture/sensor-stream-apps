package com.example.sensorrecord.presentation

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread


enum class SoundRecorderState {
    Idle, Recording
}

/**
 * A helper class to provide methods to record audio input from the MIC
 */
class SoundRecorder {

    private val _state = MutableStateFlow(SoundRecorderState.Idle)
    val state = _state.asStateFlow()

    // the default remote IP and Port to stream data
    var socketIP = "192.168.1.162"
    var socketInetAddress = InetAddress.getByName(socketIP)
    var socketPort = 50001

    companion object {
        private const val TAG = "SoundRecorder"
        private const val RECORDING_RATE = 8000 // can go up to 44K, if needed
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    /**
     * Records from the microphone.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun triggerMicStream() {
        if (_state.value == SoundRecorderState.Recording) {
            _state.value = SoundRecorderState.Idle
        } else if (_state.value == SoundRecorderState.Idle) {
            // Create an AudioRecord object for the streaming
            val intSize = 480 //AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL_IN, FORMAT)
            val audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RECORDING_RATE)
                        .setChannelMask(CHANNEL_IN)
                        .setEncoding(FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(intSize)
                .build()

            // add noise suppression and echo cancellation
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioRecord.getAudioSessionId()).setEnabled(true)
            }
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(audioRecord.getAudioSessionId()).setEnabled(true)
            }

            // begin streaming the microphone
            audioRecord.startRecording()
            Log.w(TAG, "Started Recording")
            _state.value = SoundRecorderState.Recording
            // start while loop in a thread
            thread {
                try {

                    // connect to the server
                    val udpSocket = DatagramSocket(socketPort)
                    udpSocket.broadcast = true
                    Log.v(TAG, "Beginning to broadcast UDP mesage to $socketIP:$socketPort")

                    // read from audioRecord stream and send through to TCP output stream
                    val buffer = ByteArray(intSize)
                    while (_state.value == SoundRecorderState.Recording) {
                        audioRecord.read(buffer, 0, buffer.size)
                        val dp = DatagramPacket(
                            buffer,
                            buffer.size,
                            socketInetAddress,
                            socketPort
                        )
                        udpSocket.send(dp)
                    }
                    // close connection when done
                    udpSocket.close()

                } catch (e: Exception) {
                    Log.v(TAG, "Streaming error $e")
                    _state.value = SoundRecorderState.Idle
                    Log.v(TAG, "stopped streaming")
                } finally {
                    audioRecord.release()
                }
            }
        }
    }
}