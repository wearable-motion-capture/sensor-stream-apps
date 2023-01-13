package com.example.sensorrecord.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.*

@Composable
fun SensorToggleChip(
    text: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ToggleChip(
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

@Composable
fun DataStateDisplay(state: AppState, modifier: Modifier = Modifier) {
    var color = Color.Red
    if (state == AppState.Ready) {
        color = Color.Green
    } else if (state == AppState.Processing) {
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

@Composable
fun CalibrationStateDisplay(state: CalibrationState, modifier: Modifier = Modifier) {
    var color = Color.Red
    if (state == CalibrationState.Up) {
        color = Color.Magenta
    } else if (state == CalibrationState.Down) {
        color = Color.Blue
    } else if (state == CalibrationState.Forward) {
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