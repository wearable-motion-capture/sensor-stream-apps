package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import com.mocap.watch.DataSingleton
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.DefaultText
import com.mocap.watch.ui.RedButton

@Composable
fun RenderDualMode(
    pingCallback: () -> Unit,
    calibCallback: () -> Unit,
    finishCallback: () -> Unit
) {
    val initPres by DataSingleton.CALIB_PRESS.collectAsState()
    val northDeg by DataSingleton.CALIB_NORTH.collectAsState()

    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            // Phone connection
            DefaultButton(
                onClick = { pingCallback() },
                text = "Ping Phone"
            )
        }
        item {
            // Calibrate
            DefaultText(
                text = "pres: ${"%.2f".format(initPres)} deg: ${"%.2f".format(northDeg)}"
            )
        }
        item {
            DefaultButton(
                onClick = { calibCallback() },
                text = "Recalibrate"
            )
        }
        item {
            RedButton(
                onClick = { finishCallback() },
                text = "Change Mode"
            )
        }
    }
}