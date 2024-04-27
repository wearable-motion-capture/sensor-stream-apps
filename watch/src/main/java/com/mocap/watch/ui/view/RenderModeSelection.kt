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
    watchOnlyUdpCallback: () -> Unit,
    watchViaPhoneCallback: () -> Unit,
    upperArmCallback: () -> Unit,
    pocketCallback: () -> Unit,
    selfLabellingCallback: () -> Unit
) {
    val standalone by standaloneSF.collectAsState()

    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            DefaultText(
                text = "Select MoCap Mode"
            )
        }
        item {
            DefaultButton(
                enabled = standalone,
                onClick = { watchOnlyUdpCallback() },
                text = "Watch Only"
            )
        }
        item { DefaultText(text = if (standalone) "No phone app connected" else "Found connected phone app") }
        item {
            DefaultButton(
                enabled = !standalone,
                onClick = { upperArmCallback() },
                text = "Phone Upper Arm"
            )
        }
        item {
            DefaultButton(
                enabled = !standalone,
                onClick = { pocketCallback() },
                text = "Phone Pocket"
            )
        }
        item {
            DefaultButton(
                enabled = !standalone,
                onClick = { watchViaPhoneCallback() },
                text = "Watch Only via Phone"
            )
        }
        item { DefaultText(text = "Experimental") }
        item {
            DefaultButton(
                enabled = !standalone,
                onClick = { selfLabellingCallback() },
                text = "Self-Label Recording"
            )
        }
        item {
            Text(text = "Version: ${DataSingleton.VERSION}")
        }
    }
}