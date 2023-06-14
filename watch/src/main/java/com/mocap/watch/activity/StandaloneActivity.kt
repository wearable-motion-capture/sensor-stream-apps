package com.mocap.watch.activity

import RenderStandalone
import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.ServiceBroadcastReceiver
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.viewmodel.StandaloneViewModel


class StandaloneActivity : ComponentActivity() {

    companion object {
        private const val TAG = "StandaloneActivity"  // for logging
    }

    private val _standaloneViewModel by viewModels<StandaloneViewModel>()
    private val _br =
        ServiceBroadcastReceiver(
            onServiceClose = { _standaloneViewModel.onServiceClose(it) },
            onServiceUpdate = { _standaloneViewModel.onServiceUpdate(it) }
        )

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WatchTheme {
                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // retrieve stored IP and update DataSingleton
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                val ip = sharedPref.getString(DataSingleton.IP_KEY, DataSingleton.IP_DEFAULT)
                if (ip == null) {
                    DataSingleton.setIp(DataSingleton.IP_DEFAULT)
                    DataSingleton.IP_DEFAULT
                } else {
                    DataSingleton.setIp(ip)
                }

                RenderStandalone(
                    soundStateFlow = _standaloneViewModel.audioStrState,
                    sensorStateFlow = _standaloneViewModel.sensorStrState,
                    calibCallback = {
                        startActivity(Intent("com.mocap.watch.STANDALONE_CALIBRATION"))
                    },
                    ipSetCallback = {
                        startActivity(Intent("com.mocap.watch.SET_IP"))
                    },
                    imuStreamCallback = { _standaloneViewModel.imuStreamTrigger(it) },
                    micStreamCallback = { _standaloneViewModel.audioStreamTrigger(it) },
                    finishCallback = ::finish
                )

            }
        }
    }

    /**
     * To register all listeners for all used channels
     */
    private fun registerListeners() {
        // broadcasts inform about stopped services
        val filter = IntentFilter()
        filter.addAction(DataSingleton.BROADCAST_CLOSE)
        filter.addAction(DataSingleton.BROADCAST_UPDATE)
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(_br, filter)
    }

    /**
     * clear all listeners
     */
    private fun unregisterListeners() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
        _standaloneViewModel.resetAllStreamStates()
    }

    override fun onResume() {
        super.onResume()
        registerListeners()
    }

    override fun onPause() {
        super.onPause()
        unregisterListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterListeners()
    }
}



