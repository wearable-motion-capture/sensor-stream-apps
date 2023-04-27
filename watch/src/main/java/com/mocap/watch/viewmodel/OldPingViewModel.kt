package com.mocap.watch.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar



class OldPingViewModel(application: Application) :
    AndroidViewModel(application),
    CapabilityClient.OnCapabilityChangedListener,
    MessageClient.OnMessageReceivedListener {

    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private val _connectedNodeDisplayName = MutableStateFlow("unchecked")
    val nodeName = _connectedNodeDisplayName.asStateFlow()

    private val _connectedActiveApp = MutableStateFlow(false)
    val appActive = _connectedActiveApp.asStateFlow()

    private var _connectedNode: Node? = null


    companion object {
        private const val TAG = "PingViewModel"  // for logging
    }

    override fun onCleared() {
        _scope.cancel()
    }

    fun sendPing(node: Node) {
        val message = "request"
        // sending messages and checking nodes are blocking calls,
        // so we run them in a coroutine
        _scope.launch {
            val sendMessageTask = _messageClient.sendMessage(
                node.id, DataSingleton.PING_PATH, message.toByteArray(Charsets.UTF_8)
            )
            Tasks.await(sendMessageTask)
            Log.v(TAG, "Ping sent to ${node.displayName} (${node.id})")
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

    fun queryPhoneNodes() {
        _scope.launch {
            try {

                val nodes = getCapabilitiesForReachableNodes()
                Log.d(TAG, "$nodes")
                val filterd = nodes.filterValues { "phone" in it }.keys

                if (filterd.count() > 1) {
                    throw Exception("More than one node with phone-capability detected: $filterd")
                }

                _connectedNode = filterd.first()
                _connectedNodeDisplayName.value = filterd.first().displayName

            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(TAG, "Querying nodes failed: $exception")
            }
        }
    }

    /**
     * Collects the capabilities for all nodes that are reachable using the [CapabilityClient].
     * [CapabilityClient.getAllCapabilities] returns this information as a [Map] from capabilities
     * to nodes, while this function inverts the map so we have a map of [Node]s to capabilities.
     * This form is easier to work with when trying to operate upon all [Node]s.
     */
    private suspend fun getCapabilitiesForReachableNodes(): Map<Node, Set<String>> =
        _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
            .await()
            // Pair the list of all reachable nodes with their capabilities
            .flatMap { (capability, capabilityInfo) ->
                capabilityInfo.nodes.map { it to capability }
            }
            // Group the pairs by the nodes
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )
            // Transform the capability list for each node into a set
            .mapValues { it.value.toSet() }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name == DataSingleton.PHONE_APP_ACTIVE) {
            val nodes = capabilityInfo.nodes
            if (nodes.count() > 1) {
                throw Exception("More than one node with phone-capability detected: $nodes")
            } else {
                _connectedActiveApp.value = nodes.isNotEmpty()
            }
            Log.d(TAG, "Connected app active at : $nodes")
        }
        if (capabilityInfo.name == DataSingleton.PHONE_CAPABILITY) {
            val nodes = capabilityInfo.nodes
            if (nodes.count() > 1) {
                throw Exception("More than one node with phone-capability detected: $nodes")
            } else if (nodes.isEmpty()) {
                _connectedNodeDisplayName.value = "No device"
            } else {
                _connectedNodeDisplayName.value = nodes.first().displayName
            }
            Log.d(TAG, "Connected phone : $nodes")
        }
    }

}
