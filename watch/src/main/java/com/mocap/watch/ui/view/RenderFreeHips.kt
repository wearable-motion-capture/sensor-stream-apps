package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.DefaultText
import com.mocap.watch.ui.RedButton
import com.mocap.watch.viewmodel.DualCalibrationState
import com.mocap.watch.viewmodel.FreeHipsState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun RenderFreeHips(
    appSF: StateFlow<FreeHipsState>,
    connected: StateFlow<Boolean>,
    calibrated: StateFlow<Boolean>,
    connectedNodeName: StateFlow<String>,
    gravDiff: StateFlow<Float>,
    calibTrigger: () -> Unit,
    finishCallback: () -> Unit
) {
    val appState by appSF.collectAsState()
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
                text = "BT device found:\n$nodeName",
                color = if (con) Color.Green else Color.Red,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )
        }
        item {
            Text(
                text = "%.2f".format(gravNum),
                color = Color(
                    red = max(1f - gravNum, 0f),
                    green = gravNum,
                    0f
                ),
            )
        }
        item {
            Text(
                text = when (appState) {
                    FreeHipsState.Checking ->
                        "Hold\n" +
                                "level to ground \n" +
                                "and parallel to Hip\n"

                    FreeHipsState.Streaming -> "Streaming"
                    FreeHipsState.Error -> "An Error occurred. Check log."
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )

        }
        item {
            DefaultButton(
                onClick = { calibTrigger() },
                text = "calib"
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