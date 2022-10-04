package com.example.sensorrecord.presentation

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
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
    var accSensorEventListener: SensorListener? = null
    var gravSensorEventListener: SensorListener? = null
    var gyroSensorEventListener: SensorListener? = null
    val sensorViewModel = SensorViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SensorRecordTheme {
                // access and observe sensors
                sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                registerSensorListeners()

                createFile()

                // create view and UI
                // Modifiers used by our Wear composables.
                val contentModifier = Modifier.fillMaxWidth()
                MainUI(sensorViewModel, contentModifier)

                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // keep CPU on
                //TODO: handle wake Lock properly when app shuts down
                val wakeLock: PowerManager.WakeLock =
                    (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                        newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                            acquire()
                        }
                    }

            }
        }
    }

    // Request code for creating a PDF document.
    val CREATE_FILE = 1

    private fun createFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "invoice.pdf")

        }
        startActivityForResult(intent, CREATE_FILE)
    }


    fun registerSensorListeners() {

        if (!this::sensorManager.isInitialized) {
            return
        }

        // create sensor listeners
        if (accSensorEventListener == null) {
            accSensorEventListener =
                SensorListener { sensorViewModel.onAccSensorReadout(it) }
        }
        if (gravSensorEventListener == null) {
            gravSensorEventListener =
                SensorListener { sensorViewModel.onGravSensorReadout(it) }
        }
        if (gyroSensorEventListener == null) {
            gyroSensorEventListener =
                SensorListener { sensorViewModel.onGyroSensorReadout(it) }
        }

        // register them
        sensorManager.registerListener(
            accSensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
            SensorManager.SENSOR_DELAY_FASTEST
        )
        sensorManager.registerListener(
            gravSensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
            SensorManager.SENSOR_DELAY_FASTEST
        )
        sensorManager.registerListener(
            gyroSensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }

    override fun onResume() {
        super.onResume()
        registerSensorListeners()
    }

    override fun onPause() {
        super.onPause()

        if (!this::sensorManager.isInitialized) {
            return
        } else {
            sensorManager.unregisterListener(accSensorEventListener)
            sensorManager.unregisterListener(gyroSensorEventListener)
            sensorManager.unregisterListener(gravSensorEventListener)
        }
    }
}

@Composable
fun MainUI(viewModel: SensorViewModel, modifier: Modifier = Modifier) {

    val accRead by viewModel.accReadout.collectAsState()
    val graRead by viewModel.gravReadout.collectAsState()
    val gyrRead by viewModel.gyroReadout.collectAsState()
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
        item { SensorTextDisplay("accl ${accRead}", modifier) }
        item { SensorTextDisplay("grav ${graRead}", modifier) }
        item { SensorTextDisplay("gyro ${gyrRead}", modifier) }
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

