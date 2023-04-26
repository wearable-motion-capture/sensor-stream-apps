package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mocap.watch.DataSingleton
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.DefaultText
import com.mocap.watch.ui.RedButton
import com.mocap.watch.viewmodel.PingState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderDualMode(
    pingStateFlow: StateFlow<PingState>,
    pingCallback: () -> Unit,
    calibCallback: () -> Unit,
    queryDeviceCallback: () -> Unit,
    finishCallback: () -> Unit
) {
    val initPres by DataSingleton.CALIB_PRESS.collectAsState()
    val northDeg by DataSingleton.CALIB_NORTH.collectAsState()
    val pingState by pingStateFlow.collectAsState()

    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            DisplayPingState(pingState)
        }
        item {
            // Phone connection
            DefaultButton(
                onClick = pingCallback,
                text = "Ping Phone"
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
            DefaultButton(
                onClick = queryDeviceCallback,
                text = "Query"
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

@Composable
fun DisplayPingState(state: PingState) {
    val color = when (state) {
        PingState.Connected -> Color.Green
        PingState.Error -> Color.Red
        PingState.Unchecked -> Color.Red
        PingState.Waiting -> Color.Yellow
    }
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        text = state.name,
        style = MaterialTheme.typography.body1.copy(
            color = color
        )
    )
}