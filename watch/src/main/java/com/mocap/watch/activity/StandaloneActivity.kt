package com.mocap.watch.activity

import RenderStandalone
import android.Manifest
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.preference.PreferenceManager
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.SensorListener
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.viewmodel.StandaloneViewModel


open class StandaloneActivity : ComponentActivity() {

    companion object {
        private const val TAG = "StandaloneActivity"  // for logging
    }

    private val _standaloneViewModel by viewModels<StandaloneViewModel>()
    private lateinit var _sensorManager: SensorManager

    // store listeners in this list to register and unregister them automatically
    private var _listeners = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { _standaloneViewModel.onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { _standaloneViewModel.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
        SensorListener(
            Sensor.TYPE_ACCELEROMETER
        ) { _standaloneViewModel.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { _standaloneViewModel.onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
        ) { _standaloneViewModel.onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { _standaloneViewModel.onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { _standaloneViewModel.onGyroReadout(it) },
//        SensorListener(
//            Sensor.TYPE_HEART_RATE
//        ) { globalState.onHrReadout(it) },
        SensorListener(
            69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
        ) { _standaloneViewModel.onHrRawReadout(it) }
    )

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WatchTheme {

                // retrieve stored IP and update DataSingleton
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                var storedIp = sharedPref.getString(DataSingleton.IP_KEY, DataSingleton.IP_DEFAULT)
                if (storedIp == null) {
                    storedIp = DataSingleton.IP_DEFAULT
                }
                DataSingleton.setIp(storedIp)

                // access and observe sensors
                _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                registerListeners()

                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                RenderStandalone(
                    soundStateFlow = _standaloneViewModel.soundStrState,
                    sensorStateFlow = _standaloneViewModel.sensorStrState,
                    calibCallback = {
                        startActivity(Intent("com.mocap.watch.STANDALONE_CALIBRATION"))
                    },
                    ipSetCallback = {
                        startActivity(Intent("com.mocap.watch.SET_IP"))
                    },
                    imuStreamCallback = { _standaloneViewModel.triggerImuStreamUdp(it) },
                    micStreamCallback = { _standaloneViewModel.triggerMicStream(it) },
                    finishCallback = ::finish
                )

            }
        }
    }

    /**
     * To register all listeners for all used channels
     */
    private fun registerListeners() {
        // register all listeners with their assigned codes
        if (this::_sensorManager.isInitialized) {
            // register all listeners with their assigned codes
            for (l in _listeners) {
                _sensorManager.registerListener(
                    l,
                    _sensorManager.getDefaultSensor(l.code),
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            }
        }
    }

    /**
     * clear all listeners
     */
    private fun unregisterListeners() {
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



