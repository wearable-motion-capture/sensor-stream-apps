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
import androidx.wear.compose.material.*
import com.example.sensorrecord.presentation.theme.SensorRecordTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/**
 * The main activity registers sensor listeners and creates the UI
 */
class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager
    private val sensorViewModel: SensorViewModel = SensorViewModel()
    var listeners_setup = listOf(
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { sensorViewModel.onLaccReadout(it) },
        SensorListener(
            Sensor.TYPE_ACCELEROMETER
        ) { sensorViewModel.onAcclReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD
        ) { sensorViewModel.onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { sensorViewModel.onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { sensorViewModel.onGyroReadout(it) },
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { sensorViewModel.onRotVecReadout(it) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SensorRecordTheme {
                // access and observe sensors
                sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                registerSensorListeners()

                // create view and UI
                // Modifiers used by our Wear composables.
                val contentModifier = Modifier.fillMaxWidth()
                MainUI(sensorViewModel, contentModifier)

                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // keep CPU on
//                //TODO: handle wake Lock properly when app shuts down
//                val wakeLock: PowerManager.WakeLock =
//                    (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
//                        newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
//                            acquire()
//                        }
//                    }

            }
        }
    }

    fun registerSensorListeners() {
        // register all listeners with their assigned codes
        for (l in listeners_setup) {
            sensorManager.registerListener(
                l,
                sensorManager.getDefaultSensor(l.code),
                SensorManager.SENSOR_DELAY_FASTEST
            )
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
            for (l in listeners_setup) {
                sensorManager.unregisterListener(l)
            }
        }
    }
}

@Composable
fun MainUI(viewModel: SensorViewModel, modifier: Modifier = Modifier) {

    val lacc by viewModel.linearAcc.collectAsState()
    val recordingTrigger by viewModel.recordingTrigger.collectAsState()
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
                "Record Sensors",
                recordingTrigger,
                { viewModel.recordSwitch(it) },
                modifier
            )
        }
        item { SensorTextDisplay(lacc, modifier) }
        item {
            Timer(
                startTimeStamp,
                measureInterval,
                recordingTrigger,
                { viewModel.timedSensorValues(it) },
                modifier
            )
        }
    }
}


@Composable
fun Timer(
    start: LocalDateTime,
    interval: Long = 100L,
    recordTrigger: Boolean = false,
    trigger: (Long) -> Unit,
    modifier: Modifier
) {

    // A count for measurements taken. The delay function is not accurate because estimation time
    // must be added. Therefore, we keep track of passed time in a separate calculation
    var steps by remember { mutableStateOf(0L) }

    // A while loop that doesn't block the co-routine
    LaunchedEffect(key1 = steps, key2 = recordTrigger) {
        // only do something if we want to record
        if (recordTrigger) {
            // estimate time difference to given start point as our time stamp
            val diff = Duration.between(start, LocalDateTime.now()).toMillis()
            // delay by a given amount of milliseconds
            delay(interval)
            // increase step count to trigger LaunchedEffect again
            steps += 1
            // call the event with estimated time stamp
            trigger(diff)
        }
    }
    // display elapsed time in seconds
    SensorTextDisplay(
        text = String.format(
            "%.4f",
            Duration.between(start, LocalDateTime.now()).toMillis() * 0.001
        ), modifier
    )
}

