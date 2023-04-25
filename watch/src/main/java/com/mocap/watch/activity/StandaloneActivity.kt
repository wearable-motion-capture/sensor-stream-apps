package com.mocap.watch.activity

import android.content.Intent
import android.hardware.Sensor
import android.os.*
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.mocap.watch.stateModules.PingRequester
import com.mocap.watch.stateModules.SensorListener
import com.mocap.watch.stateModules.StandaloneModule
import com.mocap.watch.ui.theme.WatchTheme


open class DualActivity : SensorActivity() {

    companion object {
        private const val TAG = "StandaloneActivity"  // for logging
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // for colours
            WatchTheme {


                val sensorStateModule = StandaloneModule()
                // make use of the SensorActivity management
                createListeners(sensorStateModule)

                val soundStateModule = StandaloneModule()


                RenderStandAlone(
                    soundStateFlow= ,
                    sensorStateFlow=stateModule.sensorStrState,
                    calibCallback = { startActivity(Intent("com.mocap.watch.activity.Calibration")) },
                    recordCallback = { stateModule.recordTrigger(it) },
                    imuStreamCallback = {stateModule.triggerImuStreamUdp },
                    micStreamCallback =
                    ipSetCallback
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

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}


