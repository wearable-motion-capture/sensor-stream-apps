package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.RedButton
import com.mocap.watch.viewmodel.DualCalibrationState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderDualCalib(
    calibStateFlow: StateFlow<DualCalibrationState>,
    calibTrigger: () -> Unit,
    calibDone: () -> Unit
) {
    val calibState by calibStateFlow.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        //autoCentering = AutoCenteringParams(itemIndex = 0),
        userScrollEnabled = true
    ) {
        item {
            Text(
                text = when (calibState) {
                    DualCalibrationState.Idle ->
                        "Hold at 90deg at \n" +
                                "chest height. \n" +
                                "Then, press:"

                    DualCalibrationState.Hold ->
                        "Keep holding at \n" +
                                "chest height"

                    DualCalibrationState.Forward ->
                        "Extend arm \n" +
                                "forward, parallel \n" +
                                "to ground. When vibrating \n" +
                                "pulse stops, wait for final vibration."

                    DualCalibrationState.Phone ->
                        "Wait for\nphone calibration."
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )

        }
        item {
            DefaultButton(
                enabled = calibState == DualCalibrationState.Idle,
                onClick = { calibTrigger() },
                text = "Start"
            )
        }
        item {
            DualCalibrationStateDisplay(
                state = calibState,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            RedButton(
                onClick = { calibDone() },
                text = "Exit"
            )
        }
    }
}


/**
 * The simple state display during the calibration procedure of the watch.
 * Switches between:
 * Hold, Forward
 * Up and down are also options, which are not part of the current routine
 */
@Composable
fun DualCalibrationStateDisplay(state: DualCalibrationState, modifier: Modifier = Modifier) {
    var color = Color.Red
    if (state == DualCalibrationState.Forward) {
        color = Color.Cyan
    }
    Text(
        modifier = modifier,
        textAlign = TextAlign.Center,
        text = state.name,
        style = MaterialTheme.typography.body1.copy(
            color = color
        )
    )
}