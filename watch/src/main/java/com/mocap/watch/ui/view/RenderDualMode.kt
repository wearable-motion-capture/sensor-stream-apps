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
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderDualMode(
    connectedNodeName: StateFlow<String>,
    appActiveStateFlow: StateFlow<Boolean>,
    calibCallback: () -> Unit,
    streamCallback: (Boolean) -> Unit,
    finishCallback: () -> Unit
) {
    val initPres by DataSingleton.CALIB_PRESS.collectAsState()
    val northDeg by DataSingleton.CALIB_NORTH.collectAsState()
    val nodeName by connectedNodeName.collectAsState()
    val appState by appActiveStateFlow.collectAsState()

    var streamCheck by remember { mutableStateOf(false) }

    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            DefaultText(text = "Connected: $nodeName")
        }
        item {
            Text(
                text = if (appState) "Phone App Active" else "Phone App Inactive",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = if (appState) Color.Green else Color.Red
            )
        }
        item {
            // Calibrate
            DefaultText(
                text = "pres: ${"%.2f".format(initPres)}" +
                        " deg: ${"%.2f".format(northDeg)}"
            )
        }
        item {
            DefaultButton(
                onClick = calibCallback,
                text = "Recalibrate"
            )
        }
        item {
            StreamToggle(enabled = true,
                text = "Stream to Phone",
                checked = streamCheck,
                onChecked = {
                    streamCheck = !streamCheck
                    streamCallback(it)
                })
        }
        item {
            RedButton(
                onClick = finishCallback,
                text = "Change Mode"
            )
        }
    }
}