package com.mocap.phone

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.modules.ServiceBroadcastReceiver
import com.mocap.phone.service.ImuService
import com.mocap.phone.viewmodel.PhoneViewModel
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderHome
import java.nio.ByteBuffer


class PhoneMain : ComponentActivity(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "PhoneMainActivity"
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _viewModel by viewModels<PhoneViewModel>()

    private val _br =
        ServiceBroadcastReceiver(
            onServiceUpdate = { _viewModel.onServiceUpdate(it) }
        )

    /** Starting point of the application */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // retrieve stored IP and update DataSingleton
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            var storedIp = sharedPref.getString(DataSingleton.IP_KEY, DataSingleton.IP_DEFAULT)
            if (storedIp == null) {
                storedIp = DataSingleton.IP_DEFAULT
            }
            DataSingleton.setIp(storedIp)

            PhoneTheme {
                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // check if a phone is connected and set state flows accordingly
                _viewModel.queryCapabilities()
                _viewModel.regularUiUpdates()

                RenderHome(
                    connectedNodeSF = _viewModel.nodeName,
                    appActiveSF = _viewModel.appActive,
                    imuSF = _viewModel.imuStreamState,
                    imuInHzSF = _viewModel.imuInHz,
                    imuOutHzSF = _viewModel.imuOutHz,
                    imuQueueSizeSF = _viewModel.imuQueueSize,
                    audioSF = _viewModel.audioStreamState,
                    audioBroadcastHzSF = _viewModel.audioBroadcastHz,
                    audioStreamQueueSF = _viewModel.audioStreamQueue,
                    ppgSF = _viewModel.ppgStreamState,
                    ppgInHzSF = _viewModel.ppgInHz,
                    ppgOutHzSF = _viewModel.ppgOutHz,
                    ppgQueueSizeSF = _viewModel.ppgQueueSize,
                    ipSetCallback = {
                        startActivity(Intent("com.mocap.phone.SET_IP"))
                    }
                )
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _viewModel.onCapabilityChanged(capabilityInfo)
    }

    /** Checks received messages for Calibration triggers */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(
            TAG, "Received from: ${messageEvent.sourceNodeId} " +
                    "with path ${messageEvent.path}"
        )
        when (messageEvent.path) {
            DataSingleton.CALIBRATION_PATH -> {
                val b = ByteBuffer.wrap(messageEvent.data)
                DataSingleton.setWatchForwardQuat(
                    floatArrayOf(
                        b.getFloat(0), b.getFloat(4),
                        b.getFloat(8), b.getFloat(12)
                    )
                )
                DataSingleton.setWatchRelPres(b.getFloat(16))

                // trigger phone calibration and pass it the node ID that sent the trigger
                val i = Intent("com.mocap.phone.CALIBRATION")
                i.putExtra("sourceNodeId", messageEvent.sourceNodeId)
                startActivity(i)
            }
        }
        _viewModel.onMessageReceived(messageEvent)
    }

    /** To register all listeners for all used channels */
    private fun registerListeners() {
        // broadcasts inform about service updates
        val filter = IntentFilter()
        filter.addAction(DataSingleton.BROADCAST_UPDATE)
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(_br, filter)

        _messageClient.addListener(this)
        // checks for a connected device with the Watch capability
        _capabilityClient.addListener(
            this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        // for the watch app to detect this phone
        _capabilityClient.addLocalCapability(DataSingleton.PHONE_APP_ACTIVE)
        // check if a phone is connected and set state flows accordingly
        _viewModel.queryCapabilities()

        val intent = Intent(this, ImuService::class.java)
        this.startService(intent)
    }

    /**
     * clear all listeners
     */
    private fun unregisterListeners() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
        _viewModel.resetStreamStates()
        _messageClient.removeListener(this)
        _capabilityClient.removeListener(this)
        _capabilityClient.removeLocalCapability(DataSingleton.PHONE_APP_ACTIVE)

        val intent = Intent(this, ImuService::class.java)
        this.stopService(intent)
    }

    override fun onResume() {
        super.onResume()
        registerListeners()
    }

    override fun onPause() {
        super.onPause()
        unregisterListeners()
    }
}