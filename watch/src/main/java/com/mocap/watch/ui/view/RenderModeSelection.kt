package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.mocap.watch.DataSingleton
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.DefaultText
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderModeSelection(
    standaloneSF: StateFlow<Boolean>,
    standaloneCallback: () -> Unit,
    dualCallback: () -> Unit
) {
    val standalone by standaloneSF.collectAsState()

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
                enabled = !standalone,
                onClick = { dualCallback() },
                text = "Dual"
            )
        }
        item { DefaultText(text = if (standalone) "No phone connected" else "Found connected phone") }
        item {
            DefaultButton(
                enabled = standalone,
                onClick = { standaloneCallback() },
                text = "Standalone"
            )
        }
        item {
            Text(text = "Version: ${DataSingleton.VERSION}")
        }
    }
}