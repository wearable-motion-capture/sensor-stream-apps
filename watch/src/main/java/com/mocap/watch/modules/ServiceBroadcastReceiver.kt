package com.mocap.watch.modules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mocap.watch.DataSingleton



class ServiceBroadcastReceiver(
    onServiceClose: (String?) -> Unit,
    onServiceUpdate: (Intent) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceBroadcastReceiver"
    }

    private val _onServiceClose = onServiceClose
    private val _onServiceUpdate = onServiceUpdate

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(
            TAG,
            "received ${intent.action} URI: ${intent.toUri(Intent.URI_INTENT_SCHEME)}"
        )

        val serviceKey = intent.extras?.getString(DataSingleton.BROADCAST_SERVICE_KEY)
        when (intent.action) {
            // when the broadcast informs about a closed service
            DataSingleton.BROADCAST_CLOSE -> _onServiceClose(serviceKey)

//            // when the broadcast updates service stats
//            DataSingleton.BROADCAST_UPDATE -> {
//                val hz = intent.extras?.getFloat(DataSingleton.BROADCAST_SERVICE_HZ)
//                val queue = intent.extras?.getInt(DataSingleton.BROADCAST_SERVICE_QUEUE)
//                _onServiceUpdate(serviceKey, hz, queue)
//            }
        }
    }
}
