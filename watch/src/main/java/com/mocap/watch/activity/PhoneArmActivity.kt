package com.mocap.watch.activity

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.ServiceBroadcastReceiver
import com.mocap.watch.modules.WatchChannelCallback
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderDual
import com.mocap.watch.viewmodel.PhoneArmViewModel


class PhoneArmActivity : ComponentActivity(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "PhoneArmActivity"  // for logging
    }

    private val _channelClient by lazy { Wearable.getChannelClient(this) }
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _phoneArmViewModel by viewModels<PhoneArmViewModel>()
    private val _channelCallback = WatchChannelCallback(
        closeCallback = { _phoneArmViewModel.onChannelClose(it) }
    )
    private val _br =
        ServiceBroadcastReceiver(
            onServiceClose = { _phoneArmViewModel.onServiceClose(it) },
            onServiceUpdate = { _phoneArmViewModel.onServiceUpdate(it) }
        )


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            // keep screen on
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // check if a phone is connected and set state flows accordingly
            _phoneArmViewModel.queryCapabilities()
            _phoneArmViewModel.regularConnectionCheck()

            val connectedNode = _phoneArmViewModel.nodeName
            val appActiveStateFlow = _phoneArmViewModel.pingSuccessState

            // for colours
            WatchTheme {
                RenderDual(
                    connectedNodeName = connectedNode,
                    appActiveStateFlow = appActiveStateFlow,
                    calibCallback = {
                        startActivity(Intent("com.mocap.watch.PHONE_ARM_CALIBRATION"))
                    },
                    imuStreamStateFlow = _phoneArmViewModel.sensorStreamState,
                    audioStreamStateFlow = _phoneArmViewModel.audioStreamState,
                    ppgStreamStateFlow = _phoneArmViewModel.ppgStreamState,
                    sensorStreamCallback = { _phoneArmViewModel.imuStreamTrigger(it) },
                    soundStreamCallback = { _phoneArmViewModel.audioStreamTrigger(it) },
                    ppgStreamCallback = { _phoneArmViewModel.ppgStreamTrigger(it) }, // PPG is only for specific watches
                    finishCallback = ::finish
                )
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        _phoneArmViewModel.onMessageReceived(messageEvent)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _phoneArmViewModel.onCapabilityChanged(capabilityInfo)
    }

    /**
     * To register all listeners for all used channels
     */
    private fun registerListeners() {
        // broadcasts inform about stopped services
        val filter = IntentFilter()
        filter.addAction(DataSingleton.BROADCAST_CLOSE)
        filter.addAction(DataSingleton.BROADCAST_UPDATE)
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(_br, filter)

        // listen for ping messages
        _messageClient.addListener(this)
        // to handle incoming data streams
        _channelClient.registerChannelCallback(_channelCallback)
        // for the phone app to detect this phone
        _capabilityClient.addLocalCapability(DataSingleton.WATCH_APP_ACTIVE)
        // checks for a connected device with the Phone capability
        _capabilityClient.addListener(
            this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        _phoneArmViewModel.queryCapabilities()
    }

    /**
     * clear all listeners
     */
    private fun unregisterListeners() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
        _phoneArmViewModel.resetAllStreamStates()
        _messageClient.removeListener(this)
        _channelClient.unregisterChannelCallback(_channelCallback)
        _capabilityClient.removeListener(this)
        _capabilityClient.removeLocalCapability(DataSingleton.WATCH_APP_ACTIVE)
    }

    override fun onResume() {
        super.onResume()
        registerListeners()
    }

    override fun onPause() {
        super.onPause()
        unregisterListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterListeners()
    }
}
