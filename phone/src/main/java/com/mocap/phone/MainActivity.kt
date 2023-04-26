package com.mocap.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.viewmodel.MainViewModel
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderHome


class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _viewModel by viewModels<MainViewModel>()

    /** Starting point of the application  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneTheme {
                RenderHome(pingCallback = { _viewModel.requestPing() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _messageClient.addListener(_viewModel)
    }

    override fun onPause() {
        super.onPause()
        _messageClient.removeListener(_viewModel)
    }
}