package com.mocap.phone.ui.view

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mocap.phone.DataSingleton
import com.mocap.phone.ImuPpgStreamState
import com.mocap.phone.SoundStreamState
import com.mocap.phone.ui.DefaultButton
import com.mocap.phone.ui.BigCard
import com.mocap.phone.ui.DefaultHeadline
import com.mocap.phone.ui.DefaultHighlight
import com.mocap.phone.ui.DefaultText
import com.mocap.phone.ui.SmallCard
import kotlinx.coroutines.flow.StateFlow

/**
 * Displays all the main functions
 */
@Composable
fun RenderHome(
    connectedNodeSF: StateFlow<String>,
    appActiveSF: StateFlow<Boolean>,
    imuPpgSF: StateFlow<ImuPpgStreamState>,
    imuPpgHzSF: StateFlow<Float>,
    imuPpgBroadcastHzSF: StateFlow<Float>,
    soundSF: StateFlow<SoundStreamState>,
    soundBroadcastHzSF: StateFlow<Float>,
    soundStreamQueueSF: StateFlow<Int>,
    imuPpgQueueSizeSF: StateFlow<Int>,
    ipSetCallback: () -> Unit
) {
    val phoneForwardQuat by DataSingleton.phoneQuat.collectAsState()
    val watchForwardQuat by DataSingleton.watchQuat.collectAsState()
    val watchPressure by DataSingleton.watchPres.collectAsState()
    val ip by DataSingleton.ip.collectAsState()

    val imuPpgSt by imuPpgSF.collectAsState()
    val nodeName by connectedNodeSF.collectAsState()
    val appState by appActiveSF.collectAsState()
    val imuPpgHz by imuPpgHzSF.collectAsState()
    val imuPpgBroadcastHz by imuPpgBroadcastHzSF.collectAsState()
    val soundSt by soundSF.collectAsState()
    val soundBroadcastHz by soundBroadcastHzSF.collectAsState()
    val soundQueueSize by soundStreamQueueSF.collectAsState()
    val queueSize by imuPpgQueueSizeSF.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        item {
            BigCard() {

                DefaultHeadline(text = "Connection")

                SmallCard() {
                    DefaultText(text = "BT device found:\n $nodeName")

                    Text(
                        text = if (appState) "Watch Dual App Active" else "Awaiting Watch App",
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center,
                        color = if (appState) Color.Green else Color.Red,
                        style = MaterialTheme.typography.h6
                    )
                }
            }
        }
        item {
            BigCard() {
                DefaultHeadline(text = "Calibration")

                SmallCard() {
                    DefaultText(
                        text = ("Phone Forward: %.2f, %.2f, %.2f, %.2f").format(
                            phoneForwardQuat[0],
                            phoneForwardQuat[1],
                            phoneForwardQuat[2],
                            phoneForwardQuat[3]
                        )
                    )
                    DefaultText(
                        text = ("Watch Forward: %.2f, %.2f, %.2f, %.2f").format(
                            watchForwardQuat[0],
                            watchForwardQuat[1],
                            watchForwardQuat[2],
                            watchForwardQuat[3]
                        )
                    )
                    DefaultText(
                        text = ("Watch Pressure: %.2f").format(watchPressure)
                    )
                }
            }
        }
        item {
            BigCard() {
                DefaultHeadline(text = "Broadcast")

                SmallCard() {
                    DefaultHighlight(text = ip)
                    DefaultButton(onClick = ipSetCallback, text = "Set target IP")
                }

                Row() {
                    SmallCard() {
                        DefaultText(text = ("IMU + PPG"))
                        DefaultHighlight(
                            text = "$imuPpgSt",
                            color = when (imuPpgSt) {
                                ImuPpgStreamState.Idle -> Color.Yellow
                                ImuPpgStreamState.Streaming -> Color.Green
                                ImuPpgStreamState.Error -> Color.Red
                            }
                        )
                        DefaultText(
                            text = "In: $imuPpgHz Hz\n " +
                                    "Out: $imuPpgBroadcastHz Hz\n" +
                                    "Queue: $queueSize"
                        )
                    }
                    SmallCard() {
                        DefaultText(text = ("Audio"))
                        DefaultHighlight(
                            text = "$soundSt",
                            color = when (soundSt) {
                                SoundStreamState.Idle -> Color.Yellow
                                SoundStreamState.Streaming -> Color.Green
                                SoundStreamState.Error -> Color.Red
                            }
                        )
                        DefaultText(
                            text = "$soundBroadcastHz Hz\n" +
                                    "Queue: $soundQueueSize"
                        )
                    }
                }

            }
        }
        item {
            Text(
                text = "Version: ${DataSingleton.VERSION}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}