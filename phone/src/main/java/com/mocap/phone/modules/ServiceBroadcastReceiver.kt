package com.mocap.phone.modules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mocap.phone.DataSingleton


class ServiceBroadcastReceiver(
    onServiceUpdate: (Intent) -> Unit
) : BroadcastReceiver() {
    companion object {
        private const val TAG = "ServiceBroadcastReceiver"
    }

    private val _onServiceUpdate = onServiceUpdate

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "received ${intent.action} URI: ${intent.toUri(Intent.URI_INTENT_SCHEME)}")
        when (intent.action) {
            // when the broadcast updates service stats
            DataSingleton.BROADCAST_UPDATE -> {
                _onServiceUpdate(intent)
            }
        }
    }
}
