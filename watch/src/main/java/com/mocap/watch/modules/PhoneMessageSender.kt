package com.mocap.watch.modules

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

object PhoneMessageSender {

    val tag = "PhoneMessageSender"
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    fun sendMessage(path: String, message: String, context: Context) {
        coroutineScope.launch {
            sendMessageInBackground(path, message, context)
        }
    }

    private fun sendMessageInBackground(path: String, message: String, context: Context) {
        //first get all the nodes, ie connected wearable devices.
        val nodeListTask = Wearable.getNodeClient(context).connectedNodes
        try {
            // Block on a task and get the result synchronously (because this is on a background
            // thread).
            val nodes = Tasks.await(nodeListTask)
            if (nodes.isEmpty()) {
                Log.i(tag, "No Node found to send message")
            }
            //Now send the message to each device.
            for (node in nodes) {
                val sendMessageTask = Wearable.getMessageClient(context)
                    .sendMessage(node.id, path, message.toByteArray())
                try {
                    // Block on a task and get the result synchronously (because this is on a background
                    // thread).
                    val result = Tasks.await(sendMessageTask)
                    Log.v(tag, "SendThread: message send to " + node.displayName)
                } catch (exception: ExecutionException) {
                    Log.e(tag, "Task failed: $exception")
                } catch (exception: InterruptedException) {
                    Log.e(tag, "Interrupt occurred: $exception")
                }
            }
        } catch (exception: ExecutionException) {
            Log.e(tag, "Task failed: $exception")
        } catch (exception: InterruptedException) {
            Log.e(
                tag, "Interrupt occurred: $exception"
            )
        }
    }
}