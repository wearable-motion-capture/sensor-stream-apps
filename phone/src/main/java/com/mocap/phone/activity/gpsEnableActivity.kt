package com.mocap.phone.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mocap.phone.ui.theme.PhoneTheme
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
//                RenderGpsSetting()
                RenderGpsUpdates()
            }
        }

    }

    override fun onPause() {
        super.onPause()
        this.finish()
    }
}