package com.mocap.watch.activity

import RenderStandalone
import android.Manifest
import android.content.Intent
import android.hardware.Sensor
import android.os.*
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import com.mocap.watch.modules.AudioModule
import com.mocap.watch.modules.SensorListener
import com.mocap.watch.modules.StandaloneModule
import com.mocap.watch.ui.theme.WatchTheme


open class StandaloneActivity : SensorActivity() {

    companion object {
        private const val TAG = "StandaloneActivity"  // for logging
    }

    // instantiate utilized state modules
    private val _sensorStateModule = StandaloneModule()
    private val _audioStateModule = AudioModule()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WatchTheme {

                // make use of the SensorActivity management
                createListeners(_sensorStateModule)

                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                RenderStandalone(
                    soundStateFlow = _audioStateModule.soundStrState,
                    sensorStateFlow = _sensorStateModule.sensorStrState,
                    calibCallback = {
                        startActivity(Intent("com.mocap.watch.activity.Calibration"))
                    },
                    ipSetCallback = {
                        startActivity(Intent("com.mocap.watch.activity.SetIP"))
                    },
                    recordCallback = { _sensorStateModule.recordTrigger(it) },
                    imuStreamCallback = { _sensorStateModule.triggerImuStreamUdp(it) },
                    micStreamCallback = { _audioStateModule.triggerMicStream(it) },
                    finishCallback = { this.finish() }
                )

            }
        }
    }

    private fun createListeners(stateModule: StandaloneModule) {
        super.setSensorListeners(
            listOf(
                SensorListener(
                    Sensor.TYPE_PRESSURE
                ) { stateModule.onPressureReadout(it) },
                SensorListener(
                    Sensor.TYPE_LINEAR_ACCELERATION
                ) { stateModule.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
                SensorListener(
                    Sensor.TYPE_ACCELEROMETER
                ) { stateModule.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
                SensorListener(
                    Sensor.TYPE_ROTATION_VECTOR
                ) { stateModule.onRotVecReadout(it) },
                SensorListener(
                    Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
                ) { stateModule.onMagnReadout(it) },
                SensorListener(
                    Sensor.TYPE_GRAVITY
                ) { stateModule.onGravReadout(it) },
                SensorListener(
                    Sensor.TYPE_GYROSCOPE
                ) { stateModule.onGyroReadout(it) },
//        SensorListener(
//            Sensor.TYPE_HEART_RATE
//        ) { globalState.onHrReadout(it) },
                SensorListener(
                    69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
                ) { stateModule.onHrRawReadout(it) }
            )
        )
//            34 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked

    }

    override fun onPause() {
        super.onPause()
        _sensorStateModule.reset()
        _audioStateModule.reset()
    }
}


