package com.mocap.phone.modules

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.mocap.phone.GlobalState
import java.util.Calendar
import kotlinx.coroutines.*


class PingRequester(globalState: GlobalState, context: Context) {

    private val _tag = "PingRequester" // for logging
    private val _pingPath = "/ping"
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)
    private val _context = context
    private val _globalState = globalState

    fun requestPing() {
        /** requests a ping from a connected device */

        val message = "request"

        coroutineScope.launch {
            //first get all the connected devices, also called nodes
            val nodeListTask = Wearable.getNodeClient(_context).connectedNodes

            // check if a single wearable is connected
            val nodes = Tasks.await(nodeListTask)
            if (nodes.isEmpty()) {
                Log.i(_tag, "No device connected")
                return@launch // exit coroutine
            } else if (nodes.count() > 1) {
                throw Exception("too many devices connected")
            }

            // send the ping request
            val node = nodes[0]
            val client = Wearable.getMessageClient(_context)
            val sendMessageTask = client.sendMessage(
                node.id, _pingPath, message.toByteArray(Charsets.UTF_8)
            )
            Tasks.await(sendMessageTask)
            Log.v(_tag, "Ping requested send to ${node.displayName} (${node.id})")
        }
    }
}

class PingResponder : WearableListenerService() {

    private val _tag = "PingResponder" // for logging
    private val _pingPath = "/ping"
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)


    override fun onMessageReceived(messageEvent: MessageEvent) {
        /** Checks received messages for _pingPath messages */
        super.onMessageReceived(messageEvent)

        val sourceId = messageEvent.sourceNodeId
        Log.i(_tag, "Message received from: $sourceId with path ${messageEvent.path}")

        if (messageEvent.path == _pingPath) {
            // if the message is a ping request, send the reply.
            when (messageEvent.data.toString(Charsets.UTF_8)) {
                "request" -> answerPing(messageEvent.sourceNodeId)
                "response" -> logPingResponse()
            }
        }
    }

    private fun logPingResponse() {
        val date = Calendar.getInstance().time
        val dateInString = date.toString()
        Log.v(_tag, "Ping response received $dateInString")
    }

    private fun answerPing(nodeId: String) {
        /** responds to a "ping request" message from the given node ID */

        val message = "response"
        coroutineScope.launch {
            val client = Wearable.getMessageClient(applicationContext)
            val sendMessageTask = client.sendMessage(
                nodeId, _pingPath, message.toByteArray(Charsets.UTF_8)
            )
            Tasks.await(sendMessageTask)
            Log.v(_tag, "Ping response sent to $nodeId")
        }
    }
}