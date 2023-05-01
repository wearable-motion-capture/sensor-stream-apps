package com.mocap.phone.activity

import android.os.*
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.mocap.phone.DataSingleton
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderIpSetting


class IpSetActivity : ComponentActivity() {

    companion object {
        private const val TAG = "IpSetActivity"  // for logging
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhoneTheme {
                RenderIpSetting(
                    setIpCallback = { setIpAndFinish(it) }
                )
            }
        }
    }

    private fun setIpAndFinish(ip: String) {
        val sharedPref = getDefaultSharedPreferences(this)
        with(sharedPref.edit()) {
            putString(DataSingleton.IP_KEY, ip)
            apply()
        }
        DataSingleton.setIp(ip)
        Log.d(TAG, "set target IP to $ip")
        this.finish() // activity done
    }

    /**
     * The sole purpose of this activity is to set the IP in sharedPref.
     * If the activity is paused, abandon the task.
     */
    override fun onPause() {
        super.onPause()
        this.finish()
    }
}
