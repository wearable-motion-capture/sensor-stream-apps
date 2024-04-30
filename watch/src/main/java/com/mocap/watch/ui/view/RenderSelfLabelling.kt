package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mocap.watch.ImuStreamState
import com.mocap.watch.ui.RedButton
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderSelfLabelling(
    connected: StateFlow<Boolean>,
    connectedNodeName: StateFlow<String>,
    imuStreamStateFlow: StateFlow<ImuStreamState>,
    recordLabel: StateFlow<String>,
    finishCallback: () -> Unit
) {
    val streamSt by imuStreamStateFlow.collectAsState()
    val nodeName by connectedNodeName.collectAsState()
    val con by connected.collectAsState()
    val label by recordLabel.collectAsState()

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
                text = if (streamSt == ImuStreamState.Streaming) "Streaming IMU" else "Not streaming",
                color = if (streamSt == ImuStreamState.Streaming) Color.Green else Color.Red,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )
        }
        item {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
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