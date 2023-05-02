package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mocap.watch.modules.CalibrationState
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.RedButton
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderStandaloneCalib(
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
                        "Hold at 90deg at \n" +
                                "chest height. \n" +
                                "Then, press:"

                    CalibrationState.Hold ->
                        "Keep holding at \n" +
                                "chest height"

                    CalibrationState.Forward ->
                        "Extend arm \n" +
                                "forward, parallel \n" +
                                "to ground. When vibrating \n" +
                                "pulse stops, wait for final vibration."
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )

        }
        item {
            DefaultButton(
                enabled = calibState == CalibrationState.Idle,
                onClick = { calibTrigger() },
                text = "Start"
            )
        }
        item {
            CalibrationStateDisplay(
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