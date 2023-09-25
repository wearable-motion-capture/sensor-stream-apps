package com.mocap.phone.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mocap.phone.service.GpsService
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.LocationUpdatesScreen
import com.mocap.phone.ui.view.RenderGpsSetting
import com.mocap.phone.ui.view.RenderGpsUpdates

class gpsEnableActivity: ComponentActivity() {

    companion object {
        private const val TAG = "gpsEnableActivity"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhoneTheme {
//                RenderGpsSetting()    // Press button to get updates
//                RenderGpsUpdates()      // Automatic Updates ~5 seconds
                LocationUpdatesScreen()     // Automatic Updates ~5 seconds
            }
        }

    }

    override fun onPause() {
        super.onPause()
        this.finish()
    }
}