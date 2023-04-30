package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.mocap.watch.DataSingleton
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.DefaultText
import com.mocap.watch.ui.RedButton
import com.mocap.watch.ui.StreamToggle
import com.mocap.watch.viewmodel.StreamState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderDual(
    connectedNodeName: StateFlow<String>,
    appActiveStateFlow: StateFlow<Boolean>,
    calibCallback: () -> Unit,
    streamStateFlow: StateFlow<StreamState>,
    streamCallback: (Boolean) -> Unit,
    finishCallback: () -> Unit
) {
    val initPres by DataSingleton.CALIB_PRESS.collectAsState()
    val calQuat by DataSingleton.forwardQuat.collectAsState()
    val nodeName by connectedNodeName.collectAsState()
    val appState by appActiveStateFlow.collectAsState()
    val streamSt by streamStateFlow.collectAsState()

    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            DefaultText(text = "Connected: $nodeName")
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
            // Calibrate
            DefaultText(
                text = "pres: %.2f \n".format(initPres) +
                        "quat: %.2f %.2f %.2f %.2f".format(
                            calQuat[0],
                            calQuat[1],
                            calQuat[2],
                            calQuat[3]
                        )
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
                text = "Stream to Phone",
                checked = (streamSt == StreamState.Streaming),
                onChecked = { streamCallback(it) }
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