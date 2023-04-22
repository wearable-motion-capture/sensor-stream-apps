package com.mocap.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.modules.WatchMessageListener
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.renderHome


class MainActivity : ComponentActivity() {

//    private val messageBroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            val message = intent?.getStringExtra(MessageConstants.message)
//            Log.i("MainActivity", "Broadcast Received {}".format(message))
//            val path = intent?.getStringExtra(MessageConstants.path)
//        }
//
//    }

    /** Starting point of the application  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneTheme {
                // render the main view
                renderHome()
            }
        }
    }

//    override fun onStart() {
//        super.onStart()
//        val intentFilter = IntentFilter(MessageConstants.intentName)
//        LocalBroadcastManager.getInstance(this)
//            .registerReceiver(messageBroadcastReceiver, intentFilter)
//
//    }
//
//    override fun onStop() {
//        super.onStop()
//        LocalBroadcastManager.getInstance(this)
//            .unregisterReceiver(messageBroadcastReceiver)
//    }
}