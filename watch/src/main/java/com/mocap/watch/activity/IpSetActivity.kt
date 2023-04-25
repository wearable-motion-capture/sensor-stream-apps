package com.mocap.watch.activity

import android.content.SharedPreferences
import android.os.*
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.mocap.watch.DataSingleton
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderIpSetting


class IpSetActivity : ComponentActivity() {

    companion object {
        private const val TAG = "IpSetActivity"  // for logging
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WatchTheme {

                val sharedPref = getDefaultSharedPreferences(this)

                RenderIpSetting(
                    setIpCallback = { setIpAndFinish(it, sharedPref = sharedPref) }
                )

            }
        }
    }

    private fun setIpAndFinish(ip: String, sharedPref: SharedPreferences) {
        with(sharedPref.edit()) {
            putString(DataSingleton.IP_KEY, ip)
            apply()
        }
        DataSingleton.setIp(ip)
        Log.d(TAG, "set target IP to $ip")
        this.finish() // activity done
    }
}
