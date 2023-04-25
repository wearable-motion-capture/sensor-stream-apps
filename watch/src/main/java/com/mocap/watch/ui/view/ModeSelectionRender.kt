package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import com.mocap.watch.AppModes
import com.mocap.watch.GlobalState

@Composable
fun RenderModeSelection() {
    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text(
                text = "Select App Mode",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Button(
                onClick = { GlobalState.setAppMode(AppModes.Dual) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Dual"
                )
            }
        }
        item {
            Button(
                onClick = { GlobalState.setAppMode(AppModes.Standalone) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Standalone"
                )
            }
        }
        item {
            Text(text = "App Version: ${GlobalState.VERSION}")
        }
    }
}