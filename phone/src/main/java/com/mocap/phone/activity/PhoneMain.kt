package com.mocap.phone.activity

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
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
import com.mocap.phone.DataSingleton
import com.mocap.phone.modules.ServiceBroadcastReceiver
import com.mocap.phone.service.AudioService
import com.mocap.phone.service.ImuService
import com.mocap.phone.service.PpgService
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
            // retrieve stored IP and PORT to update DataSingleton
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            val storedIp = sharedPref.getString(DataSingleton.IP_KEY, DataSingleton.IP_DEFAULT)
            if (storedIp != null) {
                DataSingleton.setIp(storedIp)
            }
            DataSingleton.setImuPort(sharedPref.getInt(
                DataSingleton.PORT_KEY,
                DataSingleton.IMU_PORT_DEFAULT)
            )
            DataSingleton.setRecordLocally(sharedPref.getBoolean(
                DataSingleton.RECORD_LOCALLY_KEY,
                DataSingleton.RECORD_LOCALLY_DEFAULT
            ))

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
                    },
                    imuStreamTrigger = {
                        _viewModel.sendImuTrigger()
                    }
                )
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _viewModel.onCapabilityChanged(capabilityInfo)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            // reset self-calibration count and skip calibration activity
            DataSingleton.CALIBRATION_PATH -> {
                val b = ByteBuffer.wrap(messageEvent.data)
                when (b.getInt(20)) {
                    1 -> { // lengthy calibration with vibrating pulse confirmation. Starts a separate activity
                        val i = Intent("com.mocap.phone.CALIBRATION")
                        i.putExtra("sourceNodeId", messageEvent.sourceNodeId)
                        startActivity(i)
                    }
                }
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

        val imuIntent = Intent(this, ImuService::class.java)
        this.startService(imuIntent)
        val ppgIntent = Intent(this, PpgService::class.java)
        this.startService(ppgIntent)
        val audioIntent = Intent(this, AudioService::class.java)
        this.startService(audioIntent)
    }

    /**
     * clear all listeners
     */
    private fun unregisterListeners() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
        _messageClient.removeListener(this)
        _capabilityClient.removeListener(this)
        _capabilityClient.removeLocalCapability(DataSingleton.PHONE_APP_ACTIVE)

        val imuIntent = Intent(this, ImuService::class.java)
        this.stopService(imuIntent)
        val ppgIntent = Intent(this, PpgService::class.java)
        this.stopService(ppgIntent)
        val audioIntent = Intent(this, AudioService::class.java)
        this.stopService(audioIntent)
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