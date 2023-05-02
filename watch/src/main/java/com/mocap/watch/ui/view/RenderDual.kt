package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.mocap.watch.SensorStreamState
import com.mocap.watch.SoundStreamState
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.DefaultText
import com.mocap.watch.ui.RedButton
import com.mocap.watch.ui.StreamToggle
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderDual(
    connectedNodeName: StateFlow<String>,
    appActiveStateFlow: StateFlow<Boolean>,
    calibCallback: () -> Unit,
    sensorStreamStateFlow: StateFlow<SensorStreamState>,
    soundStreamStateFlow: StateFlow<SoundStreamState>,
    sensorStreamCallback: (Boolean) -> Unit,
    soundStreamCallback: (Boolean) -> Unit,
    finishCallback: () -> Unit
) {
    val nodeName by connectedNodeName.collectAsState()
    val appState by appActiveStateFlow.collectAsState()
    val streamSt by sensorStreamStateFlow.collectAsState()
    val soundSt by soundStreamStateFlow.collectAsState()

    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            DefaultText(text = "BT device found:\n$nodeName")
        }
        item {
            Text(
                text = if (appState) "Phone App Ready" else "Phone App Inactive",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = if (appState) Color.Green else Color.Red
            )
        }
        item {
            DefaultButton(
                enabled = appState,
                onClick = calibCallback,
                text = "Recalibrate"
            )
        }
        item {
            StreamToggle(
                enabled = appState,
                text = "Stream IMU + PPG",
                checked = (streamSt == SensorStreamState.Streaming),
                onChecked = { sensorStreamCallback(it) }
            )
        }
        item {
            StreamToggle(
                enabled = appState,
                text = "Stream Audio",
                checked = (soundSt == SoundStreamState.Streaming),
                onChecked = { soundStreamCallback(it) }
            )
        }
        item {
            RedButton(
                onClick = finishCallback,
                text = "Change Mode"
            )
        }
    }
}