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
import com.mocap.watch.ui.view.RenderDualMode
import com.mocap.watch.ui.view.RenderStandAlone
import com.mocap.watch.ui.view.RenderSensorCalibration
import com.mocap.watch.ui.view.RenderIpSetting
import com.mocap.watch.ui.view.RenderModeSelection


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

    private var _listenersSetup = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { GlobalState.onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { GlobalState.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
        SensorListener(
            Sensor.TYPE_ACCELEROMETER
        ) { GlobalState.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { GlobalState.onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
        ) { GlobalState.onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { GlobalState.onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { GlobalState.onGyroReadout(it) },
//        SensorListener(
//            Sensor.TYPE_HEART_RATE
//        ) { globalState.onHrReadout(it) },
        SensorListener(
            69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
        ) { GlobalState.onHrRawReadout(it) }
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

                val sensorCalibrator = SensorCalibrator()
                val sensorDataHandler = SensorDataHandler(calibrator = sensorCalibrator)
                val soundStreamer = SoundStreamer()


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

                val currentView by GlobalState.viewState.collectAsState()

                when (currentView) {
                    Views.ModelSelection -> RenderModeSelection()

                    Views.IPsetting -> RenderIpSetting()

                    Views.Calibration -> RenderSensorCalibration(
                        calibrator = sensorCalibrator,
                        vibrator = vibrator
                    )

                    Views.StandAlone -> RenderStandAlone(
                        sensorDataHandler = sensorDataHandler,
                        soundStreamer = soundStreamer,
                        calibrator = sensorCalibrator
                    )

                    Views.DualMode -> {
                        val pingRequester = PingRequester(context = applicationContext)
                        RenderDualMode(
                            sensorDataHandler = sensorDataHandler,
                            soundStreamer = soundStreamer,
                            calibrator = sensorCalibrator,
                            pingRequester = pingRequester
                        )
                    }
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
