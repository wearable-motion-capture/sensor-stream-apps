package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import com.mocap.watch.DataSingleton
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.DefaultText

@Composable
fun RenderModeSelection(
    standaloneCallback: () -> Unit,
    dualCallback: () -> Unit
) {
    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            DefaultText(
                text = "Select App Mode"
            )
        }
        item {
            DefaultButton(
                onClick = { dualCallback() },
                text = "Dual"
            )
        }
        item {
            DefaultButton(
                onClick = { standaloneCallback() },
                text = "Standalone"
            )
        }
        item {
            Text(text = "App Version: ${DataSingleton.VERSION}")
        }
    }
}