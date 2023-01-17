package com.example.sensorrecord.presentation

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.*
import com.example.sensorrecord.presentation.theme.SensorRecordTheme


/**
 * The MainActivity is where the app starts. It creates the ViewModel, registers sensor listeners
 * and handles the UI.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity" // for logging
        private const val VERSION = "0.0.5" // a version number to confirm updates on watch screen
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var vibratorManager: VibratorManager
    private val sensorRecorder: SensorRecorder = SensorRecorder()
    private val soundRecorder: SoundRecorder = SoundRecorder()

    private var _listenersSetup = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { sensorRecorder.onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { sensorRecorder.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
        SensorListener(
            Sensor.TYPE_ACCELEROMETER
        ) { sensorRecorder.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { sensorRecorder.onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
        ) { sensorRecorder.onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { sensorRecorder.onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { sensorRecorder.onGyroReadout(it) },
        SensorListener(
            Sensor.TYPE_HEART_RATE
        ) { sensorRecorder.onHrReadout(it) },
        SensorListener(
            69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
        ) { sensorRecorder.onHrRawReadout(it) }
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
            SensorRecordTheme {

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
                sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                registerSensorListeners()


                // get the vibrator service.
                lateinit var vibrator: Vibrator
                //  This has been updated in SDK 31..
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    vibratorManager =
                        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibrator = vibratorManager.defaultVibrator
                } else {
                    // ... make sure we can work with SDK 30 as well
                    vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }


//              list all available sensors
//              //getSensorList(Sensor.TYPE_ALL) lists all the sensors present in the device
//              val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
//              for (device in deviceSensors) {
//                  println(device.toString())
//              }

                // create view and UI
                // Modifiers used by our Wear Composables.
                val contentModifier = Modifier.fillMaxWidth()

                MainUI(
                    contentModifier,
                    vibrator
                )

                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    @Composable
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun MainUI(modifier: Modifier = Modifier, vibrator: Vibrator) {

        val sensorAppState by sensorRecorder.appState.collectAsState()
        val soundRecordingState by soundRecorder.state.collectAsState()

        if (sensorAppState == SensorRecorderState.Calibrating) {
            // The calibration view
            val calibrationState by sensorRecorder.calibState.collectAsState()

            ScalingLazyColumn(
                modifier = modifier,
                autoCentering = AutoCenteringParams(itemIndex = 2)
            ) {
                item {
                    Text(VERSION)
                }
                item {
                    Text(
                        text = "Needs Calibration",
                        modifier = modifier,
                        textAlign = TextAlign.Center
                    )
                }
                item {
                    Button(
                        onClick = {
                            sensorRecorder.calibrationTrigger(vibrator)
                        }
                    ) {
                        Text(text = "Start")
                    }
                }
                item {
                    CalibrationStateDisplay(
                        state = calibrationState,
                        modifier = modifier
                    )
                }
            }
        } else {
            // all other views
            ScalingLazyColumn(
                modifier = modifier
            ) {
                item {
                    DataStateDisplay(state = sensorAppState, modifier = modifier)
                }
                item {
                    SensorToggleChip(
                        text = "Record Locally",
                        checked = (sensorAppState == SensorRecorderState.Recording),
                        onChecked = { sensorRecorder.recordTrigger(it) },
                        modifier = modifier
                    )
                }
                item {
                    SensorToggleChip(
                        text = "Stream to IP",
                        checked = (sensorAppState == SensorRecorderState.Streaming),
                        onChecked = { sensorRecorder.streamTrigger(it) },
                        modifier = modifier
                    )
                }
                item {
                    Text(
                        text = sensorRecorder.getIpPortString(),
                        modifier = modifier,
                        textAlign = TextAlign.Center
                    )
                }
                item {
                    SensorToggleChip(
                        text = "Stream MIC",
                        checked = (soundRecordingState == SoundRecorderState.Recording),
                        onChecked = { soundRecorder.triggerMicStream() },
                        modifier = modifier
                    )
                }
                item {
                    Text(
                        text = soundRecorder.getIpPortString(),
                        modifier = modifier,
                        textAlign = TextAlign.Center
                    )
                }

            }
        }
    }
}
