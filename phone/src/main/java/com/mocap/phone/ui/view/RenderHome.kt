package com.mocap.phone.ui.view

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
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
    imuSF: StateFlow<Boolean>,
    imuInHzSF: StateFlow<Float>,
    imuOutHzSF: StateFlow<Float>,
    imuQueueSizeSF: StateFlow<Int>,
    audioSF: StateFlow<Boolean>,
    audioBroadcastHzSF: StateFlow<Float>,
    audioStreamQueueSF: StateFlow<Int>,
    ppgSF: StateFlow<Boolean>,
    ppgInHzSF: StateFlow<Float>,
    ppgOutHzSF: StateFlow<Float>,
    ppgQueueSizeSF: StateFlow<Int>,
    ipSetCallback: () -> Unit,
    imuStreamTrigger: () -> Unit
) {
    val phoneForwardQuat by DataSingleton.phoneQuat.collectAsState()
    val watchForwardQuat by DataSingleton.watchQuat.collectAsState()
    val watchPressure by DataSingleton.watchPres.collectAsState()
    val ip by DataSingleton.ip.collectAsState()
    val port by DataSingleton.imuPort.collectAsState()
    val recordLocally by DataSingleton.recordLocally.collectAsState()
    val recordActivityName by DataSingleton.recordActivityName.collectAsState()

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
                DefaultHeadline(text = "Data Processing")

                SmallCard() {
                    DefaultHighlight(
                        text = if (recordLocally) "Record - $recordActivityName"  else ip
                    )
                    Text(
                        text = if (port == DataSingleton.IMU_PORT_LEFT) "Left Hand Mode"
                        else "Right Hand Mode",
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center,
                        color = if (port == DataSingleton.IMU_PORT_LEFT) Color.Yellow else Color.Magenta,
                        style = MaterialTheme.typography.h6
                    )
                    DefaultButton(onClick = ipSetCallback, text = "Set IP and Hand Mode")
                }

                Row() {
                    SmallCard() {
                        DefaultHighlight(text = ":$port")
                        DefaultText(text = ("IMU"))
                        DefaultText(
                            text = if (imuSt) "streaming" else "idle",
                            color = if (imuSt) Color.Green else Color.Yellow
                        )
                        DefaultText(
                            text = "I: $imuInHz Hz\n " +
                                    "O: $imuOutHz Hz\n" +
                                    "Queue: $imuQueueSize"
                        )
                        Button(
                            onClick = imuStreamTrigger,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(text = "trigger")
                        }
                    }
                    SmallCard() {
                        DefaultHighlight(text = ":" + DataSingleton.UDP_AUDIO_PORT.toString())
                        DefaultText(text = ("Audio"))
                        DefaultText(
                            text = if (audioSt) "streaming" else "idle",
                            color = if (audioSt) Color.Green else Color.Yellow
                        )
                        DefaultText(
                            text = "I/O: $audioBroadcastHz Hz\n" +
                                    "Queue: $audioQueueSize"
                        )
                    }
//                    SmallCard() {
//                        DefaultHighlight(text = ":" + DataSingleton.UDP_PPG_PORT.toString())
//                        DefaultText(text = ("PPG"))
//                        DefaultText(
//                            text = if (ppgSt) "streaming" else "idle",
//                            color = if (ppgSt) Color.Green else Color.Yellow
//                        )
//                        DefaultText(
//                            text = "I: $ppgInHz Hz\n " +
//                                    "O: $ppgOutHz Hz\n" +
//                                    "Queue: $ppgQueueSize"
//                        )
//                    }
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