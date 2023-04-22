package com.mocap.phone.modules

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

object MessageConstants {
    const val intentName = "WearableMessageDisplay"
    const val message = "Message"
    const val path = "Path"
}

class WatchMessageListener : WearableListenerService() {

    private val tag = "WatchMessageListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.i(tag, "Message received: ${messageEvent.toString()}")
        //broadcastMessage(messageEvent)
    }

    private fun broadcastMessage(messageEvent: MessageEvent?) {
        if (messageEvent == null) return
        val intent = Intent()
        intent.action = MessageConstants.intentName
        intent.putExtra(MessageConstants.message, String(messageEvent.data))
        intent.putExtra(MessageConstants.path, messageEvent.path)
        Log.i(tag, "Message Broadcasts")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}