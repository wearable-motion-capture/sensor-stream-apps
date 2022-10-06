package com.example.sensorrecord.presentation

import androidx.compose.runtime.Composable
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
fun StateTextDisplay(state: STATE, modifier: Modifier = Modifier) {
    var color = Color.Red
    if (state == STATE.ready) {
        color = Color.Green
    } else if (state == STATE.processing) {
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