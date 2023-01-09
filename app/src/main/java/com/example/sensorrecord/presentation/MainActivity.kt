package com.example.sensorrecord.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.*
import com.example.sensorrecord.presentation.theme.SensorRecordTheme

// for logging
private const val TAG = "MainActivity"

/**
 * The MainActivity is where the app starts. It creates the ViewModel, registers sensor listeners
 * and handles the UI.
 */
class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager
    private val sensorViewModel: SensorViewModel = SensorViewModel()

    private var _listenersSetup = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { sensorViewModel.onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { sensorViewModel.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
        SensorListener(
            Sensor.TYPE_ACCELEROMETER
        ) { sensorViewModel.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { sensorViewModel.onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
        ) { sensorViewModel.onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { sensorViewModel.onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { sensorViewModel.onGyroReadout(it) },
        SensorListener(
            Sensor.TYPE_HEART_RATE
        ) { sensorViewModel.onHrReadout(it) },
        SensorListener(
            69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
        ) { sensorViewModel.onHrRawReadout(it) }
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
            SensorRecordTheme {

                // check whether permissions for body sensors (HR) are granted
                if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 1)
                } else {
                    Log.d(TAG, "ALREADY GRANTED")
                }

                // access and observe sensors
                sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

//                // list all available sensors
//                //getSensorList(Sensor.TYPE_ALL) lists all the sensors present in the device
//                val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
//                for (device in deviceSensors) {
//                    println(device.toString())
//                }

                registerSensorListeners()

                // create view and UI
                // Modifiers used by our Wear Composables.
                val contentModifier = Modifier.fillMaxWidth()

                MainUI(sensorViewModel, contentModifier)

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
}

@Composable
fun MainUI(viewModel: SensorViewModel, modifier: Modifier = Modifier) {

    val state by viewModel.currentState.collectAsState()

    // scaling lazy column allows to scroll through items with fancy scaling when
    // they leave the screen top or bottom
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        autoCentering = AutoCenteringParams(itemIndex = 0)
    ) {
        item {
            Text("version 0.0.2")
        }
        item {
            StateTextDisplay(state = state, modifier = modifier)
        }
        item {
            SensorToggleChip(
                text = "Record Locally",
                checked = (state == STATE.Recording),
                onChecked = { viewModel.recordTrigger(it) },
                modifier = modifier
            )
        }
        item {
            SensorToggleChip(
                text = "Stream to IP",
                checked = (state == STATE.Streaming),
                onChecked = { viewModel.streamTrigger(it) },
                modifier = modifier
            )
        }
        item {
            Text(
                text = viewModel.socketIP,
                modifier = modifier,
                textAlign = TextAlign.Center
            )
        }
    }
}

