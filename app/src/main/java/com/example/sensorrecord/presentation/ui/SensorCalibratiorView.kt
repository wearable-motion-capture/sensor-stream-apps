package com.example.sensorrecord.presentation.ui

import android.os.Vibrator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.sensorrecord.presentation.modules.CalibrationState
import com.example.sensorrecord.presentation.modules.SensorCalibrator
import com.example.sensorrecord.presentation.modules.GlobalState

@Composable
fun SensorCalibrationView(
    vibrator: Vibrator,
    calibrator: SensorCalibrator,
    globalState: GlobalState
) {

    val calibState by globalState.calibState.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        //autoCentering = AutoCenteringParams(itemIndex = 0),
        userScrollEnabled = true
    ) {
        item {
            when (calibState) {
                CalibrationState.Idle -> Text(
                    text = "Hold at 90deg at\nchest height.\nThen, press:",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body1
                )
                CalibrationState.Hold -> Text(
                    text = "Keep holding at\nchest height",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body1
                )
                CalibrationState.Forward -> Text(
                    text = "Extend arm\n forward, parallel to ground. When vibrating pulse stops, wait for final vibration.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body1
                )
            }
        }
        item {
            Button(
                enabled = calibState == CalibrationState.Idle,
                onClick = {
                    calibrator.calibrationTrigger(vibrator)
                }
            ) {
                Text(text = "Start")
            }
        }
        item {
            CalibrationStateDisplay(
                state = calibState,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}