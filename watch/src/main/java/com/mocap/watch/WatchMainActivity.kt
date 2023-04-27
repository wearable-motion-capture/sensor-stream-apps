package com.mocap.watch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.preference.PreferenceManager

import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderModeSelection


/**
 * The MainActivity is where the app starts. It creates the ViewModel, registers sensor listeners
 * and handles the UI.
 */
class WatchMainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"  // for logging
    }

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

            // retrieve stored IP and update DataSingleton
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            var storedIp = sharedPref.getString(DataSingleton.IP_KEY, DataSingleton.IP_DEFAULT)
            if (storedIp == null) {
                storedIp = DataSingleton.IP_DEFAULT
            }
            DataSingleton.setIp(storedIp)

            WatchTheme {
                RenderModeSelection(
                    standaloneCallback = {
                        startActivity(Intent("com.mocap.watch.activity.Standalone"))
                    },
                    dualCallback = {
                        startActivity(Intent("com.mocap.watch.activity.Dual"))
                    }
                )
            }
        }
    }
}
