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
fun RenderSettings(setIpAndPortCallback: (String, Boolean, Boolean) -> Unit) {

    // hand mode variables
    val port by DataSingleton.imuPort.collectAsState()
    var ipText by remember { mutableStateOf(DataSingleton.ip.value) }
    val recordLocally by DataSingleton.recordLocally.collectAsState()
    var leftHandMode by remember { mutableStateOf(port == DataSingleton.IMU_PORT_LEFT) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        userScrollEnabled = false,
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
                if (!leftHandMode){
                    Text(
                        text = "Right Hand Mode is experimental. Thus far, only the Left Hand Mode is fully supported.",
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Left,
                        style = MaterialTheme.typography.body2,
                        color = Color.White
                    )
                }
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
                    checked = recordLocally,
                    onCheckedChange = { DataSingleton.setRecordLocally(!recordLocally) }
                )

                if (recordLocally) {
                    val recordActivityA by DataSingleton.recordActivityNameA.collectAsState()
                    val recordActivityB by DataSingleton.recordActivityNameB.collectAsState()
                    var expandedA by remember { mutableStateOf(false) }
                    var expandedB by remember { mutableStateOf(false) }


                    Text(
                        text = "Record Locally is for developer use only. " +
                                "Full documentation to be added in future versions. " +
                                "Select distinct activities for sequences.",
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
                            IconButton(onClick = { expandedA = !expandedA }) {
                                androidx.compose.material.Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More"
                                )
                            }
                            DropdownMenu(
                                expanded = expandedA,
                                onDismissRequest = { expandedA = false }
                            ) {
                                for (a in DataSingleton.activityOptions) {
                                    DropdownMenuItem(onClick = {
                                        DataSingleton.setRecordActivityNameA(a)
                                        expandedA = false
                                    }) {
                                        Text(a)
                                    }
                                }
                            }
                        }
                        DefaultText(text = "$recordActivityA -> $recordActivityB")
                        Row(
                            modifier = Modifier.wrapContentSize(Alignment.TopEnd)
                        ){
                            IconButton(onClick = { expandedB = !expandedB }) {
                                androidx.compose.material.Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More"
                                )
                            }
                            DropdownMenu(
                                expanded = expandedB,
                                onDismissRequest = { expandedB = false }
                            ) {
                                for (a in DataSingleton.activityOptions) {
                                    DropdownMenuItem(onClick = {
                                        DataSingleton.setRecordActivityNameB(a)
                                        expandedB = false
                                    }) {
                                        Text(a)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            DefaultButton(
                onClick = {
                    setIpAndPortCallback(
                        ipText,
                        leftHandMode,
                        recordLocally
                    )
                },
                text = "Done"
            )
        }
    }
}
