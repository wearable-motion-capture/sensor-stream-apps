package com.mocap.phone

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.modules.PhoneChannelCallback
import com.mocap.phone.viewmodel.PhoneViewModel
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderHome


class PhoneMain : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val _channelClient by lazy { Wearable.getChannelClient(this) }
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _viewModel by viewModels<PhoneViewModel>()
    private val _channelCallback = PhoneChannelCallback(
        openCallback={_viewModel.onWatchChannelOpen(it)},
        closeCallback={_viewModel.onWatchChannelClose(it)},
    )

    /** Starting point of the application  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneTheme {
                RenderHome()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _channelClient.registerChannelCallback(_channelCallback)
        _capabilityClient.addListener(
            _viewModel,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        _capabilityClient.addLocalCapability(DataSingleton.PHONE_APP_ACTIVE)
    }

    override fun onPause() {
        super.onPause()
        _channelClient.unregisterChannelCallback(_channelCallback)
        _capabilityClient.removeListener(_viewModel)
        _capabilityClient.removeLocalCapability(DataSingleton.PHONE_APP_ACTIVE)
    }
}