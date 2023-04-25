package com.mocap.watch.stateModules

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.mocap.watch.GlobalState
import com.mocap.watch.GlobalState.PING_PATH
import java.util.Calendar
import kotlinx.coroutines.*


class PingRequester(context: Context) {
    companion object {
        private const val TAG = "PingRequester"
    }

    private val _messageClient by lazy { Wearable.getMessageClient(context) }
    private val _nodeClient by lazy { Wearable.getNodeClient(context) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

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
                Log.d(TAG, "No device connected")
                return@launch // exit coroutine
            } else if (nodes.count() > 1) {
                throw Exception("too many devices connected")
            }

            // send the ping request
            val node = nodes[0]
            val sendMessageTask = _messageClient.sendMessage(
                node.id, PING_PATH, message.toByteArray(Charsets.UTF_8)
            )
            Tasks.await(sendMessageTask)
            Log.v(TAG, "Ping requested send to ${node.displayName} (${node.id})")
        }
    }
}


class PingHandlerService : WearableListenerService() {
    /**
     * This service is initialized through the AndroidManifest
     */

    companion object {
        private const val TAG = "PingHandlerService"
    }

    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        /** Checks received messages for PingPath messages */
        super.onMessageReceived(messageEvent)

        val sourceId = messageEvent.sourceNodeId
        Log.d(TAG, "Message received from: $sourceId with path ${messageEvent.path}")

        when (messageEvent.path) {
            PING_PATH ->
                when (messageEvent.data.toString(Charsets.UTF_8)) {
                    // if the message is a ping request, send the reply.
                    "request" -> answerPing(messageEvent.sourceNodeId)
                    // if the message is a ping reply, the ping was successful
                    "response" -> logPing()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _scope.cancel()
    }

    private fun logPing() {
        /** Update the global state Ping timestamp */
        val date = Calendar.getInstance().time
        val dateInString = date.toString()
        GlobalState.setLastPingResponse(dateInString)
        Log.d(TAG, "Ping received $dateInString")
    }

    private fun answerPing(nodeId: String) {
        /** responds to a "ping request" message from the given node ID */
        val message = "response"

        _scope.launch {
            val sendMessageTask = _messageClient.sendMessage(
                nodeId, PING_PATH, message.toByteArray(Charsets.UTF_8)
            )
            Tasks.await(sendMessageTask)
            Log.d(TAG, "Ping response sent to $nodeId")
            logPing()
        }
    }
}