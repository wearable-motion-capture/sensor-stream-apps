package com.mocap.watch.activity

import android.Manifest
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import com.mocap.watch.modules.SensorListener
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.WatchChannelCallback
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderDual
import com.mocap.watch.viewmodel.DualViewModel


class DualActivity : ComponentActivity() {

    companion object {
        private const val TAG = "DualActivity"  // for logging
    }

    private val _channelClient by lazy { Wearable.getChannelClient(this) }
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _dualViewModel by viewModels<DualViewModel>()
    private val _channelCallback = WatchChannelCallback(
        closeCallback = { _dualViewModel.onChannelClose(it) }
    )
    private lateinit var _sensorManager: SensorManager

    // store listeners in this list to register and unregister them automatically
    private var _listeners = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { _dualViewModel.onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { _dualViewModel.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
        SensorListener(
            Sensor.TYPE_ACCELEROMETER
        ) { _dualViewModel.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { _dualViewModel.onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
        ) { _dualViewModel.onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { _dualViewModel.onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { _dualViewModel.onGyroReadout(it) },
//        SensorListener(
//            Sensor.TYPE_HEART_RATE
//        ) { globalState.onHrReadout(it) },
        SensorListener(
            69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
        ) { _dualViewModel.onHrRawReadout(it) }
    )

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // for colours
            WatchTheme {

                // access and observe sensors
                _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                registerSensorListeners()

                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // check if a phone is connected and set state flows accordingly
                _dualViewModel.queryCapabilities()
                _dualViewModel.regularConnectionCheck()
                val connectedNode = _dualViewModel.nodeName
                val appActiveStateFlow = _dualViewModel.appActive

                RenderDual(
                    connectedNodeName = connectedNode,
                    appActiveStateFlow = appActiveStateFlow,
                    calibCallback = {
                        startActivity(Intent("com.mocap.watch.DUAL_CALIBRATION"))
                    },
                    sensorStreamStateFlow = _dualViewModel.sensorStreamState,
                    soundStreamStateFlow = _dualViewModel.soundStreamState,
                    sensorStreamCallback = { _dualViewModel.sensorStreamTrigger(it) },
                    soundStreamCallback = { _dualViewModel.audioStreamTrigger(it) },
                    finishCallback = ::finish
                )
            }
        }
    }

    private fun registerSensorListeners() {
        // register all listeners with their assigned codes
        for (l in _listeners) {
            _sensorManager.registerListener(
                l,
                _sensorManager.getDefaultSensor(l.code),
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    /**
     * To register all listeners for all used channels
     */
    private fun registerListeners() {
        _messageClient.addListener(_dualViewModel)
        // to handle incoming data streams
        _channelClient.registerChannelCallback(_channelCallback)
        // for the phone app to detect this phone
        _capabilityClient.addLocalCapability(DataSingleton.WATCH_APP_ACTIVE)
        // checks for a connected device with the Phone capability
        _capabilityClient.addListener(
            _dualViewModel,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        // register all listeners with their assigned codes
        if (this::_sensorManager.isInitialized) {
            registerSensorListeners()
        }
    }

    /**
     * clear all listeners
     */
    private fun unregisterListeners() {
        _messageClient.removeListener(_dualViewModel)
        _channelClient.unregisterChannelCallback(_channelCallback)
        _capabilityClient.removeListener(_dualViewModel)
        _capabilityClient.removeLocalCapability(DataSingleton.WATCH_APP_ACTIVE)
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) {
                _sensorManager.unregisterListener(l)
            }
        }
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
