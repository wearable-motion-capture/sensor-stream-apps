package com.example.sensorrecord.presentation.ui

import android.os.Vibrator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
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
        autoCentering = AutoCenteringParams(itemIndex = 1),
        userScrollEnabled = false
    ) {
        item {
            Text(
                text = "Hold at chest height,\nthen press:",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
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