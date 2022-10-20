package com.example.sensorrecord.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.*
import com.example.sensorrecord.presentation.theme.SensorRecordTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/**
 * The MainActivity is where the app starts. It creates the ViewModel, registers sensor listeners
 * and handles the UI.
 */
class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager
    private val sensorViewModel: SensorViewModel = SensorViewModel()
    private var _listenersSetup = listOf(
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
            Sensor.TYPE_PRESSURE
        ) { sensorViewModel.onPressureReadout(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SensorRecordTheme {
                // access and observe sensors
                sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager


                // getSensorList(Sensor.TYPE_ALL) lists all the sensors present in the device
//                val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
//                for (device in deviceSensors) {
//                    println(device.toString())
//                }

                registerSensorListeners()

                // create view and UI
                // Modifiers used by our Wear composables.
                val contentModifier = Modifier.fillMaxWidth()
                MainUI(sensorViewModel, contentModifier)

                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // keep CPU on
//                //TODO: It seems, the above flag is enough - if shutdowns still happen, handle wake Lock properly with a release()
//                val wakeLock: PowerManager.WakeLock =
//                    (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
//                        newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
//                            acquire()
//                        }
//                    }

            }
        }
    }

    private fun registerSensorListeners() {
        // register all listeners with their assigned codes
        for (l in _listenersSetup) {
            val check = sensorManager.registerListener(
                l,
                sensorManager.getDefaultSensor(l.code),
                SensorManager.SENSOR_DELAY_FASTEST
            )
            if (check) {
                println("device has %s".format(l.code));
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
    val startTimeStamp by viewModel.startTimeStamp.collectAsState()
    val measureInterval =
        10L // The interval in milliseconds between every sensor readout (1000/interval = Hz)


    // scaling lazy column allows to scroll through items with fancy scaling when
    // they leave the screen top or bottom
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        autoCentering = AutoCenteringParams(itemIndex = 0)
    ) {
        item {
            SensorToggleChip(
                text = "Record Sensors",
                checked = (state == STATE.recording),
                onChecked = { viewModel.recordTrigger(it) },
                modifier = modifier
            )
        }
        item {
            SensorTimer(
                start = startTimeStamp,
                interval = measureInterval,
                state = state,
                onTick = { viewModel.recordSensorValues(it) },
                modifier = modifier
            )
        }
        item {
            StateTextDisplay(state = state, modifier = modifier)
        }
    }
}


@Composable
        /**
         * Some sensors observe in distinct frequencies (Hz). This timer collects measurements from all sensors
         * in a fixed given interval to sync data collection.
         */
fun SensorTimer(
    start: LocalDateTime,
    interval: Long = 100L,
    state: STATE = STATE.ready,
    onTick: (Long) -> Unit,
    modifier: Modifier
) {

    // A count for measurements taken. The delay function is not accurate because estimation time
    // must be added. Therefore, we keep track of passed time in a separate calculation
    var steps by remember { mutableStateOf(0L) }

    // A while loop that doesn't block the co-routine
    LaunchedEffect(key1 = steps, key2 = state) {
        // only do something if we want to record
        if (state == STATE.recording) {
            // estimate time difference to given start point as our time stamp
            val diff = Duration.between(start, LocalDateTime.now()).toMillis()
            // delay by a given amount of milliseconds
            delay(interval)
            // increase step count to trigger LaunchedEffect again
            steps += 1
            // call the event with estimated time stamp
            onTick(diff)
        }
    }
    // display elapsed time in seconds
    Text(
        modifier = modifier,
        textAlign = TextAlign.Center,
        text = "%.4f ".format(
            Duration.between(start, LocalDateTime.now()).toMillis() * 0.001
        )
    )
}

