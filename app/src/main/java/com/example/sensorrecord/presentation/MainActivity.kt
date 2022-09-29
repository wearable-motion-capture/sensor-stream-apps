package com.example.android.wearable.sensorrecord

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.*
import com.example.sensorrecord.presentation.theme.SensorRecordTheme


/**
 * The main activity registers sensor listeners and creates the UI
 */
class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager
    private var accSensor: Sensor? = null
    var accSensorEventListener: SensorListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SensorRecordTheme {
                // val makes it a final (cannot be initialized in a different way afterwards)
                val sensorModel = SensorViewModel()
                val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val accSensor: Sensor =
                    sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
                val accSensorEventListener = SensorListener({ sensorModel.onAccSensorReadout(it) })

                // register listener with sensor manager.
                sensorManager.registerListener(
                    accSensorEventListener,
                    accSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )

                val readout by sensorModel.accReadout.collectAsState()
                val recording by sensorModel.recording.collectAsState()

                // Modifiers used by our Wear composables.
                val contentModifier = Modifier
                    .fillMaxWidth()

                // scaling lazy column allows to scroll through items with fancy scaling when
                // they leave the screen top or bottom
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    autoCentering = AutoCenteringParams(itemIndex = 0)
                ) {
                    item {
                        ToggleChip(
                            "Record Sensors",
                            recording,
                            { sensorModel.recordSwitch(it) },
                            contentModifier
                        )
                    }
                    item { TextDisplay(readout, contentModifier) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (accSensorEventListener != null) {
            sensorManager.registerListener(
                accSensorEventListener,
                accSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(accSensorEventListener)
    }
}