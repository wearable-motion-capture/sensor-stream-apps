package com.mocap.watch.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class PingState {
    Unchecked,
    Error,
    Waiting,
    Connected
}


class PingViewModel(application: Application) :
    AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _nodeClient by lazy { Wearable.getNodeClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private val _connected = MutableStateFlow(PingState.Unchecked)
    val connected = _connected.asStateFlow()


    companion object {
        private const val TAG = "PingViewModel"  // for logging
    }

    override fun onCleared() {
        _scope.cancel()
    }

    fun requestPing() {
        /**
         * This function assumes that only one device is connected and
         * requests a ping from that connected device.
         * If more devices are connected, it will raise an exception
         */

        val message = "request"

        // sending messages and checking nodes are blocking calls,
        // so we run them in a coroutine
        _scope.launch {
            //first get all the connected devices, also called nodes
            val nodeListTask = _nodeClient.connectedNodes

            // check if a single device is connected
            val nodes = Tasks.await(nodeListTask)
            if (nodes.isEmpty()) {
                _connected.value = PingState.Error
                Log.d(TAG, "No device connected")
                return@launch // exit coroutine
            } else if (nodes.count() > 1) {
                _connected.value = PingState.Error
                Log.d(TAG, "too many devices connected")
                return@launch // exit coroutine
            }

            // send the ping request
            _connected.value = PingState.Waiting
            val node = nodes[0]
            val sendMessageTask = _messageClient.sendMessage(
                node.id, DataSingleton.PING_PATH, message.toByteArray(Charsets.UTF_8)
            )
            Tasks.await(sendMessageTask)
            Log.v(TAG, "Ping requested send to ${node.displayName} (${node.id})")
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        /** Checks received messages for PingPath messages */

        val sourceId = messageEvent.sourceNodeId
        Log.d(TAG, "Message received from: $sourceId with path ${messageEvent.path}")

        when (messageEvent.path) {
            DataSingleton.PING_PATH ->
                when (messageEvent.data.toString(Charsets.UTF_8)) {
                    // if the message is a ping request, send the reply.
                    "request" -> answerPing(messageEvent.sourceNodeId)
                    // if the message is a ping reply, the ping was successful
                    "response" -> logPing()
                }
        }
    }

    private fun logPing() {
        /** Update the global state Ping timestamp */
        val date = Calendar.getInstance().time
        val dateInString = date.toString()
        Log.d(TAG, "Ping received $dateInString")
        _connected.value = PingState.Connected
    }

    private fun answerPing(nodeId: String) {
        /** responds to a "ping request" message from the given node ID */
        val message = "response"

        _scope.launch {
            val sendMessageTask = _messageClient.sendMessage(
                nodeId, DataSingleton.PING_PATH, message.toByteArray(Charsets.UTF_8)
            )
            Tasks.await(sendMessageTask)
            Log.d(TAG, "Ping response sent to $nodeId")
            logPing()
        }
    }
}