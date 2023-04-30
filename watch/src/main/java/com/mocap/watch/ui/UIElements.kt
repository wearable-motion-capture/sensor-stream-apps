package com.mocap.watch.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.*


/**The basic toggle chips to start/stop recording and streaming */
@Composable
fun StreamToggle(
    enabled: Boolean,
    text: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    ToggleChip(
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        checked = checked,
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.switchIcon(checked = checked),
                contentDescription = if (checked) "On" else "Off"
            )
        },
        onCheckedChange = {
            onChecked(it)
        },
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
fun DefaultText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
fun DefaultButton(enabled: Boolean = true, onClick: () -> Unit, text: String) {
    Button(
        enabled = enabled,
        onClick = { onClick() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = text)
    }
}

@Composable
fun RedButton(onClick: () -> Unit, text: String) {
    Button(
        onClick = { onClick() },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
    ) {
        Text(text = text)
    }
}