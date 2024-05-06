package com.mocap.phone.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.mocap.phone.DataSingleton
import com.mocap.phone.ui.DefaultButton
import com.mocap.phone.ui.DefaultHeadline
import com.mocap.phone.ui.DefaultText
import com.mocap.phone.ui.SmallCard


@Composable
fun RenderSettings(saveSettingsCallback: (String, Boolean, Boolean, Boolean) -> Unit) {

    // hand mode variables
    val port by DataSingleton.imuPort.collectAsState()
    var ipText by remember { mutableStateOf(DataSingleton.ip.value) }
    val recordLocally by DataSingleton.recordLocally.collectAsState()
    val mediaButtons by DataSingleton.listenToMediaButtons.collectAsState()
    var leftHandMode by remember { mutableStateOf(port == DataSingleton.IMU_PORT_LEFT) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            DefaultHeadline(text = "Set Target IP")
        }
        item {
            SmallCard() {
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("Set IP to stream to") },
                    textStyle = TextStyle(color = Color.White),
                    singleLine = true
                )
            }
        }


        item {
            DefaultHeadline(text = "Data Processing Modes")
        }
        item {
            SmallCard() {
                Text(
                    text = if (leftHandMode) "Left Hand Mode"
                    else "Right Hand Mode",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = if (leftHandMode) Color.Yellow else Color.Magenta,
                    style = MaterialTheme.typography.h6
                )
                androidx.compose.material.Switch(
                    checked = !leftHandMode,
                    onCheckedChange = { leftHandMode = !leftHandMode }
                )

                if (!leftHandMode) {
                    Text(
                        text = "Right Hand Mode is experimental. Thus far, only the Left Hand Mode is fully supported.",
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Left,
                        style = MaterialTheme.typography.body2,
                        color = Color.White
                    )
                }

            }
        }

        item {
            SmallCard() {
                Text(
                    text = if (recordLocally) "Record Locally" else "Broadcast",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = if (recordLocally) Color.Yellow else Color.Magenta,
                    style = MaterialTheme.typography.h6
                )
                androidx.compose.material.Switch(
                    checked = !recordLocally,
                    onCheckedChange = {
                        DataSingleton.setRecordLocally(!recordLocally)
                    }
                )

                if (recordLocally) {
                    Text(
                        text = "Record Locally is for developer use only. " +
                                "Full documentation to be added in future versions.",
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Left,
                        style = MaterialTheme.typography.body2,
                        color = Color.White
                    )

                    Text(
                        text = if (mediaButtons) "Media Buttons" else "Fixed Label",
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center,
                        color = if (mediaButtons) Color.White else Color.White,
                        style = MaterialTheme.typography.h6
                    )
                    androidx.compose.material.Switch(
                        checked = mediaButtons,
                        onCheckedChange = { DataSingleton.setListenToMediaButtons(!mediaButtons) }
                    )


                    if (!mediaButtons) {
                        val recordActivity by DataSingleton.recordActivityLabel.collectAsState()
                        var expandedLabels by remember { mutableStateOf(false) }

                        Text(
                            text = "Select distinct activities for sequences.",
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Left,
                            style = MaterialTheme.typography.body2,
                            color = Color.White
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.wrapContentSize(Alignment.TopEnd)
                            ) {
                                IconButton(onClick = { expandedLabels = !expandedLabels }) {
                                    androidx.compose.material.Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More"
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedLabels,
                                    onDismissRequest = { expandedLabels = false }
                                ) {
                                    for (a in DataSingleton.activityLabels) {
                                        DropdownMenuItem(onClick = {
                                            DataSingleton.setRecordActivityLabel(a)
                                            expandedLabels = false
                                        }) {
                                            Text(a)
                                        }
                                    }
                                }
                            }
                            DefaultText(text = recordActivity)
                        }
                    } else {
                        Text(
                            text = "Cycle through labels by pressing the Play/Pause \n" +
                                    "button of a connected BT device",
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Left,
                            style = MaterialTheme.typography.body2,
                            color = Color.White
                        )
                    }
                }
            }
        }

        item {
            DefaultButton(
                onClick = {
                    saveSettingsCallback(
                        ipText,
                        leftHandMode,
                        recordLocally,
                        mediaButtons
                    )
                },
                text = "Done"
            )
        }
    }
}
