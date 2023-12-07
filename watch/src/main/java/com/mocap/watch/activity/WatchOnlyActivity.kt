package com.mocap.watch.activity

import RenderWatchOnly
import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.SensorListener
import com.mocap.watch.modules.ServiceBroadcastReceiver
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.viewmodel.WatchOnlyViewModel


class WatchOnlyActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WatchOnlyActivity"  // for logging
    }

    private val _viewModel by viewModels<WatchOnlyViewModel>()
    private lateinit var _sensorManager: SensorManager
    private var _listeners = listOf<SensorListener>()
    private val _br =
        ServiceBroadcastReceiver(
            onServiceClose = { _viewModel.onServiceClose(it) },
            onServiceUpdate = { _viewModel.onServiceUpdate(it) }
        )

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            // keep screen on
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // retrieve stored IP and update DataSingleton
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            val ip = sharedPref.getString(DataSingleton.IP_KEY, DataSingleton.IP_DEFAULT)
            if (ip == null) {
                DataSingleton.setIp(DataSingleton.IP_DEFAULT)
                DataSingleton.IP_DEFAULT
            } else {
                DataSingleton.setIp(ip)
            }

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

            registerListeners()
            registerIMUListeners()
            _viewModel.gravityCheck()

            WatchTheme {
                RenderWatchOnly(
                    audioStreamStateFlow = _viewModel.audioStrState,
                    imuStreamStateFlow = _viewModel.sensorStrState,
                    calibrated = _viewModel.calSuccessState,
                    gravDiff = _viewModel.gravDiff,
                    ipSetCallback = {
                        startActivity(Intent("com.mocap.watch.SET_IP"))
                    },
                    imuStreamCallback = { _viewModel.imuStreamTrigger(it) },
                    audioStreamCallback = { _viewModel.audioStreamTrigger(it) },
                    finishCallback = ::finish
                )
            }
        }
    }

    private fun registerIMUListeners() {
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) {
                _sensorManager.registerListener(
                    l,
                    _sensorManager.getDefaultSensor(l.code),
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            }
        }
    }

    private fun unregisterIMUListeners() {
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) _sensorManager.unregisterListener(l)
        }
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
    }

    /**
     * clear all listeners
     */
    private fun unregisterListeners() {
        unregisterIMUListeners()
        _viewModel.resetAllStreamStates()
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
    }

    override fun onResume() {
        super.onResume()
        registerIMUListeners()
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



