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
import androidx.compose.runtime.*
import com.mocap.watch.modules.PingRequester

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

    private lateinit var _sensorManager: SensorManager
    private lateinit var _vibratorManager: VibratorManager

    private val _globalState = GlobalState()
    private val _sensorCalibrator = SensorCalibrator(globalState = _globalState)
    private val _sensorDataHandler = SensorDataHandler(
        globalState = _globalState,
        calibrator = _sensorCalibrator
    )
    private val soundStreamer = SoundStreamer(globalState = _globalState)

    private var _listenersSetup = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { _globalState.onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { _globalState.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
        SensorListener(
            Sensor.TYPE_ACCELEROMETER
        ) { _globalState.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { _globalState.onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
        ) { _globalState.onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { _globalState.onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { _globalState.onGyroReadout(it) },
//        SensorListener(
//            Sensor.TYPE_HEART_RATE
//        ) { globalState.onHrReadout(it) },
        SensorListener(
            69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
        ) { _globalState.onHrRawReadout(it) }
    )

    //    private var _listenersSetup = listOf(
//        DebugSensorListener(
//            34 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
//        )
//    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // for colours
            WatchTheme {

                // check whether permissions for body sensors (HR) are granted
                if (
                    (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) ||
                    (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                ) {
                    requestPermissions(
                        arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.RECORD_AUDIO),
                        1
                    )
                } else {
                    Log.d(TAG, "body sensor and audio recording permissions already granted")
                }

                // access and observe sensors
                _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                registerSensorListeners()

                val pingRequester = PingRequester(
                    globalState = _globalState,
                    context = applicationContext
                )


                // get the vibrator service.
                lateinit var vibrator: Vibrator
                //  This has been updated in SDK 31..
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    _vibratorManager =
                        getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibrator = _vibratorManager.defaultVibrator
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

                val currentView by _globalState.viewState.collectAsState()

                if (currentView == Views.Calibration) {
                    RenderSensorCalibration(
                        globalState = _globalState,
                        calibrator = _sensorCalibrator,
                        vibrator = vibrator
                    )
                } else if (currentView == Views.Home) {
                    RenderHome(
                        globalState = _globalState,
                        sensorDataHandler = _sensorDataHandler,
                        soundStreamer = soundStreamer,
                        calibrator = _sensorCalibrator,
                        pingRequester = pingRequester
                    )
                } else if (currentView == Views.IPsetting) {
                    RenderIpSetting(
                        globalState = _globalState
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
            val check = _sensorManager.registerListener(
                l,
                _sensorManager.getDefaultSensor(l.code),
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
        if (!this::_sensorManager.isInitialized) {
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
        if (!this::_sensorManager.isInitialized) {
            return
        } else {
            // unregister listeners
            for (l in _listenersSetup) {
                _sensorManager.unregisterListener(l)
            }
        }
    }
}
