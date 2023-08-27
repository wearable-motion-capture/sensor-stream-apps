package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mocap.watch.AudioStreamState
import com.mocap.watch.ImuStreamState
import com.mocap.watch.ui.RedButton
import com.mocap.watch.ui.StreamToggle
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderPhonePocket(
    connected: StateFlow<Boolean>,
    connectedNodeName: StateFlow<String>,
    calibrated: StateFlow<Boolean>,
    gravDiff: StateFlow<Float>,
    imuStreamStateFlow: StateFlow<ImuStreamState>,
    audioStreamStateFlow: StateFlow<AudioStreamState>,
    imuStreamCallback: (Boolean) -> Unit,
    audioStreamCallback: (Boolean) -> Unit,
    finishCallback: () -> Unit
) {
    val streamSt by imuStreamStateFlow.collectAsState()
    val soundSt by audioStreamStateFlow.collectAsState()
    val nodeName by connectedNodeName.collectAsState()
    val gravNum by gravDiff.collectAsState()
    val con by connected.collectAsState()
    val cal by calibrated.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        userScrollEnabled = true
    ) {
        item {
            Text(
                text = if (con) "Phone App Active\n$nodeName" else "Phone App Not Active",
                color = if (con) Color.Green else Color.Red,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )
        }
        item {
            Text(
                text = if (cal) "Posture Good" else "Hold watch level to ground and parallel to Hip\n" +
                        "%.2f".format(gravNum),
                color = if (cal) Color.Green else Color.Red,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )
        }
        item {
            StreamToggle(
                enabled = (con and cal),
                text = "Stream IMU",
                checked = (streamSt == ImuStreamState.Streaming),
                onChecked = { imuStreamCallback(it) }
            )
        }
        item {
            StreamToggle(
                enabled = (con and cal),
                text = "Stream Audio",
                checked = (soundSt == AudioStreamState.Streaming),
                onChecked = { audioStreamCallback(it) }
            )
        }
        item {
            RedButton(
                onClick = { finishCallback() },
                text = "Exit"
            )
        }
    }
}