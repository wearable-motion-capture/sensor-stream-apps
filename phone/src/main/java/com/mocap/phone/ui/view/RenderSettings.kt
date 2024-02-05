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

import androidx.compose.foundation.layout.Box

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
    val recordLocally by DataSingleton.recordLocally.collectAsState()
    var leftHandMode by remember { mutableStateOf(port == DataSingleton.IMU_PORT_LEFT) }
    val recordActivity by DataSingleton.recordActivityName.collectAsState()

    // formatting
    var ipText by remember { mutableStateOf(DataSingleton.ip.value) }

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
            DefaultHeadline(text = "Set Hand Mode")
        }
        item {
            SmallCard() {
                Text(
                    text = "Right Hand Mode is experimental. Thus far, only the Left Hand Mode is fully supported.",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Left,
                    style = MaterialTheme.typography.body2,
                    color = Color.White
                )
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
                var expanded by remember { mutableStateOf(false) }
                Text(
                    text = "Record Locally is for developer use only. Full documentation to be added in future versions.",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Left,
                    style = MaterialTheme.typography.body2,
                    color = Color.White
                )
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

                DefaultText(text = recordActivity)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.TopEnd)
                ) {
                    IconButton(onClick = { expanded = !expanded }) {
                        androidx.compose.material.Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More"
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        for (a in DataSingleton.activityOptions) {
                            DropdownMenuItem(onClick = {
                                DataSingleton.setRecordActivityName(a)
                                expanded = false
                            }) {
                                Text(a)
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
