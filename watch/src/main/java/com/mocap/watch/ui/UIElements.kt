package com.mocap.watch.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.*
import com.mocap.watch.modules.CalibrationState
import com.mocap.watch.modules.SensorDataHandlerState


/**
 * The basic toggle chips to start/stop recording and streaming
 */
@Composable
fun SensorToggleChip(
    enabled: Boolean,
    text: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ToggleChip(
        enabled = enabled,
        modifier = modifier,
        checked = checked,
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.switchIcon(checked = checked),
                contentDescription = if (checked) "On" else "Off"
            )
        },
        onCheckedChange = onChecked,
        label = {
            Text(
                text = text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/**
 * The simple state display when recording or streaming data. Switches between:
 * ready, recording/streaming, processing
 */
@Composable
fun DataStateDisplay(state: SensorDataHandlerState, modifier: Modifier = Modifier) {
    var color = Color.Red
    if (state == SensorDataHandlerState.Idle) {
        color = Color.Green
    } else if (state == SensorDataHandlerState.Processing) {
        color = Color.Yellow
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