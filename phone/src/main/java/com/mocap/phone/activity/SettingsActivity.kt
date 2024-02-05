package com.mocap.phone.activity

import android.os.*
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.mocap.phone.DataSingleton
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderSettings


class SettingsActivity : ComponentActivity() {

    companion object {
        private const val TAG = "IpSetActivity"  // for logging
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhoneTheme {
                RenderSettings(
                    setIpAndPortCallback = this::setIpPort
                )
            }
        }
    }

    private fun setIpPort(ip: String, leftHandMode: Boolean, recordLocally : Boolean) {

        // Make sure the text contains a valid IP
        var confirmedIp = ip
        val regex = """^((25[0-5]|(2[0-4]|1\d|[1-9]|)\d)\.?\b){4}$""".toRegex()
        if (!regex.containsMatchIn(ip)) {
            confirmedIp = "ERROR - Malformed"
        }
        // decide on port based on hand mode
        var p = DataSingleton.IMU_PORT_RIGHT
        if (leftHandMode) {
            p = DataSingleton.IMU_PORT_LEFT
        }

        // update shared preferences
        val sharedPref = getDefaultSharedPreferences(this)
        with(sharedPref.edit()) {
            putInt(DataSingleton.PORT_KEY, p)
            putString(DataSingleton.IP_KEY, confirmedIp)
            putBoolean(DataSingleton.RECORD_LOCALLY_KEY, recordLocally)
            apply()
        }

        // update data singleton
        DataSingleton.setImuPort(p)
        DataSingleton.setIp(ip)
        DataSingleton.setRecordLocally(recordLocally)

        Log.d(TAG, "set target IP to $ip and IMU PORT to $p and record locally to $recordLocally")
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
