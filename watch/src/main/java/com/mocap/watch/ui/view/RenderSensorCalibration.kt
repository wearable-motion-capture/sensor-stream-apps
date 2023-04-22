package com.mocap.watch.ui.view

import android.os.Vibrator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mocap.watch.CalibrationState
import com.mocap.watch.GlobalState
import com.mocap.watch.modules.SensorCalibrator
import com.mocap.watch.ui.CalibrationStateDisplay

@Composable
fun RenderSensorCalibration(
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
            Text(
                text = when (calibState) {
                    CalibrationState.Idle ->
                        "Hold at 90deg at\n" +
                                "chest height.\n" +
                                "Then, press:"

                    CalibrationState.Hold ->
                        "Keep holding at\n" +
                                "chest height"

                    CalibrationState.Forward ->
                        "Extend arm\n" +
                                "forward, parallel to ground. " +
                                "When vibrating pulse stops, wait for final vibration."
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
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