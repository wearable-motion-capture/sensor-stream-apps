package com.mocap.watch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.preference.PreferenceManager
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text

import com.mocap.watch.ui.theme.WatchTheme


/**
 * The MainActivity is where the app starts. It creates the ViewModel, registers sensor listeners
 * and handles the UI.
 */
class MainActivity : ComponentActivity() {

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


            // keep screen on
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            WatchTheme {
                Button(
                    onClick = { startActivity(Intent("com.mocap.watch.activity.Standalone")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Standalone")
                }
            }
        }
    }
}
