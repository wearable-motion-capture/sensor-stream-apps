package com.mocap.watch.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.preference.PreferenceManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton

import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderModeSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


/**
 * The MainActivity is where the app starts. For the watch app, this is the main selection between
 * Dual and Standalone mode
 */
class WatchModeSelection : ComponentActivity(),
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "MainActivity"  // for logging
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private val _standalone = MutableStateFlow(true) // whether standalone mode is available

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            // check whether permissions for body sensors (HR) are granted
            if (
                (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) ||
                (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.RECORD_AUDIO),
                    1
                )
            } else {
                Log.d(TAG, "body sensor and audio recording permissions already granted")
            }

            // retrieve stored values from shared preferences and update DataSingleton
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            DataSingleton.setExpMode(
                sharedPref.getBoolean(
                    DataSingleton.EXP_MODE_KEY,
                    DataSingleton.EXP_MODE_KEY_DEFAULT
                )
            )


            // keep screen on
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            queryCapabilities() // check if a phone is connected
            WatchTheme {
                RenderModeSelection(
                    standaloneSF = _standalone,
                    watchOnlyUdpCallback = { startActivity(Intent("com.mocap.watch.WATCH_ONLY")) },
                    watchViaPhoneCallback = { startActivity(Intent("com.mocap.watch.WATCH_ONLY_VIA_PHONE")) },
                    upperArmCallback = { startActivity(Intent("com.mocap.watch.PHONE_ARM")) },
                    pocketCallback = { startActivity(Intent("com.mocap.watch.PHONE_POCKET")) },
                    selfLabellingCallback = { startActivity(Intent("com.mocap.watch.SELF_LABELLING")) },
                    expModeCallback = { expModeSelect(it) }
                )
            }
        }
    }

    private fun expModeSelect(b: Boolean) {
        // update Data Singleton and shared preferences to store the decision
        DataSingleton.setExpMode(b)
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        with(sharedPref.edit()) {
            putBoolean(DataSingleton.EXP_MODE_KEY, b)
            apply()
        }

    }

    private fun queryCapabilities() {
        _scope.launch {
            try {
                val task = _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
                val res = Tasks.await(task)
                for ((_, v) in res.iterator()) onCapabilityChanged(v) // handle in callback
            } catch (exception: Exception) {
                Log.d(TAG, "Querying nodes failed: $exception")
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val deviceCap = DataSingleton.PHONE_CAPABILITY
        // this checks if a phone is available at all
        when (capabilityInfo.name) {
            deviceCap -> {
                val nodes = capabilityInfo.nodes
                if (nodes.count() > 1) {
                    throw Exception("More than one node with $deviceCap detected: $nodes")
                } else _standalone.value = nodes.isEmpty()
            }
        }
    }

    private fun register() {
        _capabilityClient.addListener(
            this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        queryCapabilities()
    }

    private fun unregister() {
        _capabilityClient.removeListener(this)
    }

    override fun onResume() {
        super.onResume()
        register()
    }

    override fun onPause() {
        super.onPause()
        unregister()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregister()
    }
}
