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
import com.mocap.phone.ImuStreamState
import com.mocap.phone.PpgStreamState
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
    imuSF: StateFlow<ImuStreamState>,
    imuInHzSF: StateFlow<Float>,
    imuOutHzSF: StateFlow<Float>,
    imuQueueSizeSF: StateFlow<Int>,
    audioSF: StateFlow<SoundStreamState>,
    audioBroadcastHzSF: StateFlow<Float>,
    audioStreamQueueSF: StateFlow<Int>,
    ppgSF: StateFlow<PpgStreamState>,
    ppgInHzSF: StateFlow<Float>,
    ppgOutHzSF: StateFlow<Float>,
    ppgQueueSizeSF: StateFlow<Int>,
    ipSetCallback: () -> Unit
) {
    val phoneForwardQuat by DataSingleton.phoneQuat.collectAsState()
    val watchForwardQuat by DataSingleton.watchQuat.collectAsState()
    val watchPressure by DataSingleton.watchPres.collectAsState()
    val ip by DataSingleton.ip.collectAsState()

    val nodeName by connectedNodeSF.collectAsState()
    val appState by appActiveSF.collectAsState()

    val imuSt by imuSF.collectAsState()
    val imuInHz by imuInHzSF.collectAsState()
    val imuOutHz by imuOutHzSF.collectAsState()
    val imuQueueSize by imuQueueSizeSF.collectAsState()

    val ppgSt by ppgSF.collectAsState()
    val ppgInHz by ppgInHzSF.collectAsState()
    val ppgOutHz by ppgOutHzSF.collectAsState()
    val ppgQueueSize by ppgQueueSizeSF.collectAsState()

    val audioSt by audioSF.collectAsState()
    val audioBroadcastHz by audioBroadcastHzSF.collectAsState()
    val audioQueueSize by audioStreamQueueSF.collectAsState()

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
                    DefaultButton(onClick = ipSetCallback, text = "Set Target IP")
                }

                Row() {
                    SmallCard() {
                        DefaultHighlight(text = ":"+DataSingleton.UDP_IMU_PORT.toString())
                        DefaultText(text = ("IMU"))
                        DefaultText(
                            text = "$imuSt",
                            color = when (imuSt) {
                                ImuStreamState.Idle -> Color.Yellow
                                ImuStreamState.Streaming -> Color.Green
                                ImuStreamState.Error -> Color.Red
                            }
                        )
                        DefaultText(
                            text = "I: $imuInHz Hz\n " +
                                    "O: $imuOutHz Hz\n" +
                                    "Queue: $imuQueueSize"
                        )
                    }
                    SmallCard() {
                        DefaultHighlight(text = ":"+DataSingleton.UDP_AUDIO_PORT.toString())
                        DefaultText(text = ("Audio"))
                        DefaultText(
                            text = "$audioSt",
                            color = when (audioSt) {
                                SoundStreamState.Idle -> Color.Yellow
                                SoundStreamState.Streaming -> Color.Green
                                SoundStreamState.Error -> Color.Red
                            }
                        )
                        DefaultText(
                            text = "I/O: $audioBroadcastHz Hz\n" +
                                    "Queue: $audioQueueSize"
                        )
                    }
                    SmallCard() {
                        DefaultHighlight(text = ":"+DataSingleton.UDP_PPG_PORT.toString())
                        DefaultText(text = ("PPG"))
                        DefaultText(
                            text = "$ppgSt",
                            color = when (ppgSt) {
                                PpgStreamState.Idle -> Color.Yellow
                                PpgStreamState.Streaming -> Color.Green
                                PpgStreamState.Error -> Color.Red
                            }
                        )
                        DefaultText(
                            text = "I: $ppgInHz Hz\n " +
                                    "O: $ppgOutHz Hz\n" +
                                    "Queue: $ppgQueueSize"
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