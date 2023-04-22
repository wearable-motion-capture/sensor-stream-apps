package com.mocap.watch

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*

import com.mocap.watch.modules.SensorCalibrator
import com.mocap.watch.modules.SensorDataHandler
import com.mocap.watch.modules.SensorListener
import com.mocap.watch.modules.SoundStreamer

import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderHome
import com.mocap.watch.ui.view.RenderSensorCalibration
import com.mocap.watch.ui.view.RenderIpSetting


/**
 * The MainActivity is where the app starts. It creates the ViewModel, registers sensor listeners
 * and handles the UI.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"  // for logging
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var vibratorManager: VibratorManager

    private val globalState: GlobalState = GlobalState()

    private val sensorCalibrator: SensorCalibrator = SensorCalibrator(
        globalState = globalState
    )
    private val sensorDataHandler: SensorDataHandler = SensorDataHandler(
        globalState = globalState,
        calibrator = sensorCalibrator
    )
    private val soundStreamer: SoundStreamer = SoundStreamer(
        globalState = globalState
    )

    private var _listenersSetup = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { globalState.onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { globalState.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
        SensorListener(
            Sensor.TYPE_ACCELEROMETER
        ) { globalState.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { globalState.onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
        ) { globalState.onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { globalState.onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { globalState.onGyroReadout(it) },
//        SensorListener(
//            Sensor.TYPE_HEART_RATE
//        ) { globalState.onHrReadout(it) },
        SensorListener(
            69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
        ) { globalState.onHrRawReadout(it) }
    )

    //    private var _listenersSetup = listOf(
//        DebugSensorListener(
//            34 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
//        )
//    )

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // for colours
            WatchTheme {

                // check whether permissions for body sensors (HR) are granted
                if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 1)
                } else {
                    Log.d(TAG, "body sensor permission already granted")
                }

                // check whether permissions for recording audio are granted
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                } else {
                    Log.d(TAG, "record audio permission already granted")
                }

                // access and observe sensors
                sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                registerSensorListeners()


                // get the vibrator service.
                lateinit var vibrator: Vibrator
                //  This has been updated in SDK 31..
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    vibratorManager =
                        getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibrator = vibratorManager.defaultVibrator
                } else {
                    // we require the compatibility with SDK 20 for our galaxy watch 4
                    vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                }


//              list all available sensors
//              //getSensorList(Sensor.TYPE_ALL) lists all the sensors present in the device
//              val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
//              for (device in deviceSensors) {
//                  println(device.toString())
//              }

                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                val currentView by globalState.viewState.collectAsState()

                if (currentView == Views.Calibration) {
                    RenderSensorCalibration(
                        globalState = globalState,
                        calibrator = sensorCalibrator,
                        vibrator = vibrator
                    )
                } else if (currentView == Views.Home) {
                    RenderHome(
                        globalState = globalState,
                        sensorDataHandler = sensorDataHandler,
                        soundStreamer = soundStreamer,
                        calibrator = sensorCalibrator,
                        context = this
                    )
                } else if (currentView == Views.IPsetting) {
                    RenderIpSetting(
                        globalState = globalState
                    )
                }
            }
        }
    }

    /**
     * Called on app startup and whenever app resumes
     */
    private fun registerSensorListeners() {
        // register all listeners with their assigned codes
        for (l in _listenersSetup) {
            val check = sensorManager.registerListener(
                l,
                sensorManager.getDefaultSensor(l.code),
                SensorManager.SENSOR_DELAY_FASTEST
            )
            if (check) {
                println("device has %s".format(l.code))
            }
        }
    }

    /**
     * Re-register SensorListeners when app starts up from background
     */
    override fun onResume() {
        super.onResume()
        if (!this::sensorManager.isInitialized) {
            return
        } else {
            registerSensorListeners()
        }
    }

    /**
     * Unregister SensorListeners when app is in background
     */
    override fun onPause() {
        super.onPause()
        if (!this::sensorManager.isInitialized) {
            return
        } else {
            // unregister listeners
            for (l in _listenersSetup) {
                sensorManager.unregisterListener(l)
            }
        }
    }
}
