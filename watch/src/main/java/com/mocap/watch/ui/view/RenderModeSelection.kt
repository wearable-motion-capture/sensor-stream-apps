package com.mocap.watch.ui.view

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Checkbox
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
    selfLabellingCallback: () -> Unit,
    expModeCallback: (Boolean) -> Unit
) {
    val standalone by standaloneSF.collectAsState()
    val expMode by DataSingleton.expMode.collectAsState()

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
            DefaultText(
                text = if (standalone) "No phone connected. Available mode:" else "Found connected phone. Available modes:",
                color = Color.Cyan
            )
        }
        if (standalone) {
            item {
                DefaultButton(
                    onClick = { watchOnlyUdpCallback() },
                    text = "Watch Only"
                )
            }
        } else {
            item {
                DefaultButton(
                    onClick = { upperArmCallback() },
                    text = "Upper Arm"
                )
            }
            item {
                DefaultButton(
                    onClick = { pocketCallback() },
                    text = "Pocket"
                )
            }

            item {
                Row(Modifier.padding(8.dp)) {
                    Checkbox(
                        checked = expMode,
                        onCheckedChange = { expModeCallback(it) }
                    )
                    Text(
                        text = "Experimental",
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (expMode) {
                item {
                    DefaultButton(
                        onClick = { watchViaPhoneCallback() },
                        text = "Watch Only via Phone"
                    )
                }

                item {
                    DefaultButton(
                        onClick = { selfLabellingCallback() },
                        text = "Self-Label Recording"
                    )
                }
            }
        }




        item {
            Text(text = "Version: ${DataSingleton.VERSION}")
        }

    }
}