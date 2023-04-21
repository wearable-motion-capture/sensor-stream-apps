package com.mocap.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderHome


class MainActivity : ComponentActivity() {
    /**
     * Starting point of the application
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneTheme {
                // render the main view
                RenderHome()
            }
        }
    }
}