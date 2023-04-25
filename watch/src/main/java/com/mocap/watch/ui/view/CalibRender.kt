package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mocap.watch.stateModules.CalibrationState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CalibRender(
    calibStateFlow: StateFlow<CalibrationState>,
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
                onClick = { calibTrigger() }
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
        item {
            Button(
                onClick = { calibDone() }
            ) {
                Text(text = "Exit")
            }
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
fun CalibrationStateDisplay(state: CalibrationState, modifier: Modifier = Modifier) {
    var color = Color.Red
    if (state == CalibrationState.Forward) {
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