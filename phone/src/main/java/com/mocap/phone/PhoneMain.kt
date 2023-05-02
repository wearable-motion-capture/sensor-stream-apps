package com.mocap.phone

import android.content.Intent
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.modules.PhoneChannelCallback
import com.mocap.phone.viewmodel.PhoneViewModel
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderHome
import java.nio.ByteBuffer


class PhoneMain : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "PhoneMainActivity"
    }

    private val _channelClient by lazy { Wearable.getChannelClient(this) }
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }

    private val _viewModel by viewModels<PhoneViewModel>()
    private val _channelCallback = PhoneChannelCallback(
        openCallback = { _viewModel.onChannelOpen(it) },
        closeCallback = { _viewModel.onChannelClose(it) }
    )

    // System services not available to Activities before onCreate()
    private lateinit var _sensorManager: SensorManager

    /** Starting point of the application  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            // register sensor listeners manually after startup
            registerSensorListeners()

            // retrieve stored IP and update DataSingleton
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            var storedIp = sharedPref.getString(DataSingleton.IP_KEY, DataSingleton.IP_DEFAULT)
            if (storedIp == null) {
                storedIp = DataSingleton.IP_DEFAULT
            }
            DataSingleton.setIp(storedIp)

            // starts a loop to update frequencies
            _viewModel.updateHzCounts()

            PhoneTheme {
                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // check if a phone is connected and set state flows accordingly
                _viewModel.queryCapabilities()

                RenderHome(
                    connectedNodeSF = _viewModel.nodeName,
                    appActiveSF = _viewModel.appActive,
                    imuPpgSF = _viewModel.imuPpgStreamState,
                    imuPpgHzSF = _viewModel.imuPpgStreamHz,
                    imuPpgBroadcastHzSF = _viewModel.imuPpgBroadcastHz,
                    soundSF = _viewModel.soundStreamState,
                    soundBroadcastHzSF = _viewModel.soundBroadcastHz,
                    queueSizeSF = _viewModel.queueSize,
                    ipSetCallback = {
                        startActivity(Intent("com.mocap.phone.SET_IP"))
                    }
                )
            }
        }
    }

    /**
     * Checks received messages for Calibration triggers
     */
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
    }


    /**
     * To register sensor listeners manually after onCreate
     */
    private fun registerSensorListeners() {
        if (this::_sensorManager.isInitialized) {
            for (l in _viewModel.listeners) {
                _sensorManager.registerListener(
                    l,
                    _sensorManager.getDefaultSensor(l.code),
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            }
        }
    }

    /**
     * To register all listeners for all used channels
     */
    private fun registerListeners() {
        // register all listeners with their assigned codes
        registerSensorListeners()
        _messageClient.addListener(this)
        // checks for a connected device with the Watch capability
        _capabilityClient.addListener(
            _viewModel,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        // for the watch app to detect this phone
        _capabilityClient.addLocalCapability(DataSingleton.PHONE_APP_ACTIVE)
        // check if a phone is connected and set state flows accordingly
        _viewModel.queryCapabilities()
        // to handle incoming data streams
        _channelClient.registerChannelCallback(_channelCallback)
    }

    /**
     * clear all listeners
     */
    private fun unregisterListeners() {
        if (this::_sensorManager.isInitialized) {
            for (l in _viewModel.listeners) _sensorManager.unregisterListener(l)
        }
        _messageClient.removeListener(this)
        _capabilityClient.removeListener(_viewModel)
        _capabilityClient.removeLocalCapability(DataSingleton.PHONE_APP_ACTIVE)
        _channelClient.unregisterChannelCallback(_channelCallback)
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