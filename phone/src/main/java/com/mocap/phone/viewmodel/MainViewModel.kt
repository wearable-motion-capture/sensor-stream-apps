package com.mocap.phone.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar


class MainViewModel( application: Application) :
    AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _nodeClient by lazy { Wearable.getNodeClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    companion object {
        private const val TAG = "ViewModel"
    }

    /**
     * This function assumes that only one device is connected and
     * requests a ping from that connected device.
     * If more devices are connected, it will raise an exception
     */
    fun requestPing() {
        val message = "request"

        // sending messages and checking nodes are blocking calls,
        // so we run them in a coroutine
        _scope.launch {
            //first get all the connected devices, also called nodes
            val nodeListTask = _nodeClient.connectedNodes

            // check if a single device is connected
            val nodes = Tasks.await(nodeListTask)
            if (nodes.isEmpty()) {
                Log.d(TAG, "No device connected")
                return@launch // exit coroutine
            } else if (nodes.count() > 1) {
                throw Exception("too many devices connected")
            }

            // send the ping request
            val node = nodes[0]
            val sendMessageTask = _messageClient.sendMessage(
                node.id, DataSingleton.PING_PATH, message.toByteArray(Charsets.UTF_8)
            )
            Tasks.await(sendMessageTask)
            Log.v(TAG, "Ping requested send to ${node.displayName} (${node.id})")
        }
    }

    /** Checks received messages for PingPath messages */
    override fun onMessageReceived(messageEvent: MessageEvent) {

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

    /** Update the global state Ping timestamp */
    private fun logPing() {

        val date = Calendar.getInstance().time
        val dateInString = date.toString()
        DataSingleton.setLastPingResponse(dateInString)
        Log.d(TAG, "Ping received $dateInString")
    }

    /** responds to a "ping request" message from the given node ID */
    private fun answerPing(nodeId: String) {

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