package com.mocap.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mocap.phone.modules.PingRequester
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderHome


class MainActivity : ComponentActivity() {

    /** Starting point of the application  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneTheme {
                val globalState = GlobalState()
                val pingRequester = PingRequester(
                    globalState = globalState,
                    context = applicationContext
                )
                // render the main view
                RenderHome(pingRequester)
            }
        }
    }
}