package com.mocap.phone.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.concurrent.thread

enum class StreamState {
    Idle, // app waits for watch to trigger the streaming
    Error, // error state. Stop streaming
    Streaming // streaming to IP and Port set in StateModule
}

class PhoneViewModel(application: Application) :
    AndroidViewModel(application),
    CapabilityClient.OnCapabilityChangedListener {

    private val _channelClient by lazy { Wearable.getChannelClient(application) }

    companion object {
        private const val TAG = "PhoneViewModel"
    }

    private val _streamState = MutableStateFlow(StreamState.Idle)
    val streamState = _streamState.asStateFlow()


    fun onWatchChannelOpen(channel: Channel) {
        Log.d(TAG, "Channel Opened to ${channel.nodeId}")
        thread {
            // get the input stream from the opened channel
            val streamTask = _channelClient.getInputStream(channel)
            val stream = Tasks.await(streamTask)

            _streamState.value = StreamState.Streaming
            while (streamState.value == StreamState.Streaming) {
                if (stream.available() > 0) {
                    // read input stream message into buffer
                    val buffer = ByteBuffer.allocate(4 * DataSingleton.WATCH_MESSAGE_SIZE)
                    stream.read(buffer.array(), 0, buffer.capacity())
                    Log.d(TAG, "${buffer.getFloat(0)} : ${stream.available()}")
                }
            }
            stream.close()
        }
    }

    fun onWatchChannelClose(channel: Channel) {
        _streamState.value = StreamState.Idle
        Log.d(TAG, "Channel Closed to ${channel.nodeId}")
    }

    /**
     * We don't really do anything here because capabilities are not expected to change
     */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
    }
}