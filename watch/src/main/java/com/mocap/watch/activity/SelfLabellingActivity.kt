package com.mocap.watch.activity

import android.Manifest
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.input.WearableButtons
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.SensorListener
import com.mocap.watch.modules.ServiceBroadcastReceiver
import com.mocap.watch.modules.WatchChannelCallback
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderSelfLabelling
import com.mocap.watch.viewmodel.PhonePocketViewModel


class SelfLabellingActivity : ComponentActivity(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "SelfLabelling"  // for logging
    }

    private val _channelClient by lazy { Wearable.getChannelClient(this) }
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _viewModel by viewModels<PhonePocketViewModel>()
    private var _listeners = listOf<SensorListener>()
    private lateinit var _sensorManager: SensorManager
    private val _channelCallback = WatchChannelCallback(
        closeCallback = { _viewModel.onChannelClose(it) }
    )
    private val _br =
        ServiceBroadcastReceiver(
            onServiceClose = { _viewModel.onServiceClose(it) },
            onServiceUpdate = { _viewModel.onServiceUpdate(it) }
        )

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            val count = WearableButtons.getButtonCount(this.baseContext)

            if (count > 1) {
                Log.d(TAG, "there are ${count} buttons available")
            }

            val buttonInfo = WearableButtons.getButtonInfo(this, KeyEvent.KEYCODE_STEM_1)
            Log.d(TAG, "KEYCODE_STEM_1 is present on the device")

            // add Sensor Listeners with our calibrator callbacks
            _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            _listeners = listOf(
                SensorListener(
                    Sensor.TYPE_PRESSURE
                ) { _viewModel.onPressureReadout(it) },
                SensorListener(
                    Sensor.TYPE_ROTATION_VECTOR
                ) { _viewModel.onRotVecReadout(it) },
                SensorListener(
                    Sensor.TYPE_GRAVITY
                ) { _viewModel.onGravReadout(it) }
            )

            // with the vibration service, create the view model
            _viewModel.queryCapabilities()
            _viewModel.regularConnectionCheck()
            registerListeners()
            registerIMUListeners()
            _viewModel.gravityCheck()

            // keep screen on to not enter ambient mode
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WatchTheme {
                RenderSelfLabelling(
                    connected = _viewModel.pingSuccessState,
                    connectedNodeName = _viewModel.nodeName,
                    calibrated = _viewModel.calSuccessState,
                    gravDiff = _viewModel.gravDiff,
                    imuStreamStateFlow = _viewModel.sensorStreamState,
                    imuStreamCallback = { _viewModel.imuStreamTrigger(it) },
                    finishCallback = ::finish
                )
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        _viewModel.onMessageReceived(messageEvent)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _viewModel.onCapabilityChanged(capabilityInfo)
    }

    private fun registerIMUListeners() {
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) {
                if (_sensorManager.getDefaultSensor(l.code) != null) {
                    _sensorManager.registerListener(
                        l,
                        _sensorManager.getDefaultSensor(l.code),
                        SensorManager.SENSOR_DELAY_FASTEST
                    )
                } else {
                    throw Exception("Sensor code ${l.code} is not present on this device")
                }
            }
        }
    }

    private fun unregisterIMUListeners() {
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) _sensorManager.unregisterListener(l)
        }
    }

    /**
     * Register all listeners with their assigned codes.
     * Called on app startup and whenever app resumes
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
        _viewModel.queryCapabilities()
    }

    /**
     * Unregister listeners and cancel vibration signals when exiting
     * the calibration activity
     */
    private fun unregisterListeners() {
        unregisterIMUListeners()
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
        _viewModel.resetAllStreamStates()
        _messageClient.removeListener(this)
        _channelClient.unregisterChannelCallback(_channelCallback)
        _capabilityClient.removeListener(this)
        _capabilityClient.removeLocalCapability(DataSingleton.WATCH_APP_ACTIVE)
        this.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterListeners()
    }

    override fun onPause() {
        super.onPause()
        unregisterListeners()
    }

    override fun onResume() {
        super.onResume()
        registerIMUListeners()
        registerListeners()
    }
}
