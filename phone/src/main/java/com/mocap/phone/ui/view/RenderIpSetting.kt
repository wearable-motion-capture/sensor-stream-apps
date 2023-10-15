package com.mocap.phone.ui.view

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.*
import com.mocap.phone.DataSingleton
import com.mocap.phone.ui.BigCard
import com.mocap.phone.ui.DefaultButton
import com.mocap.phone.ui.DefaultHeadline
import com.mocap.phone.ui.DefaultText
import com.mocap.phone.ui.SmallCard


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RenderIpSetting(setIpAndPortCallback: (String, Boolean, Boolean) -> Unit) {

    // hand mode variables
    val port by DataSingleton.imuPort.collectAsState()
    val recordLocally by DataSingleton.recordLocally.collectAsState()
    var leftHandMode by remember { mutableStateOf(port == DataSingleton.IMU_PORT_LEFT) }
    val recordActivity by DataSingleton.recordActivityName.collectAsState()

    // formatting
    val ipStr by DataSingleton.ip.collectAsState()
    val ipSplit = ipStr.split(".")
    var selectedColumn by remember { mutableStateOf(0) }
    val textStyle = MaterialTheme.typography.body1

    val firstState =
        rememberPickerState(
            initialNumberOfOptions = 1000,
            initiallySelectedOption = ipSplit[0].toInt()
        )
    val firstContentDescription by remember {
        derivedStateOf { "${firstState.selectedOption}" }
    }
    val secondState =
        rememberPickerState(
            initialNumberOfOptions = 1000,
            initiallySelectedOption = ipSplit[1].toInt()
        )
    val secondContentDescription by remember {
        derivedStateOf { "${secondState.selectedOption}" }
    }
    val thirdState =
        rememberPickerState(
            initialNumberOfOptions = 1000,
            initiallySelectedOption = ipSplit[2].toInt()
        )
    val thirdContentDescription by remember {
        derivedStateOf { "${thirdState.selectedOption}" }
    }
    val fourthState =
        rememberPickerState(
            initialNumberOfOptions = 1000,
            initiallySelectedOption = ipSplit[3].toInt()
        )
    val fourthContentDescription by remember {
        derivedStateOf { "${fourthState.selectedOption}" }
    }

    val pickerW = 32.dp
    val picherH = 100.dp


    @Composable
    fun Option(column: Int, text: String) = Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text, style = textStyle,
            color = if (selectedColumn == column) MaterialTheme.colors.secondary
            else MaterialTheme.colors.onBackground,
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize()
                .pointerInteropFilter {
                    if (it.action == MotionEvent.ACTION_DOWN) {
                        selectedColumn = column
                    }
                    true
                }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        userScrollEnabled = false,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            DefaultHeadline(text = "Set Local IP")
        }
        item {
            BigCard() {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Picker(
                        readOnly = selectedColumn != 0,
                        state = firstState,
                        modifier = Modifier.size(pickerW, picherH),
                        contentDescription = firstContentDescription,
                        option = { first: Int -> Option(0, "%3d".format(first)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = ".", style = textStyle, color = MaterialTheme.colors.onBackground)
                    Spacer(Modifier.width(8.dp))
                    Picker(
                        readOnly = selectedColumn != 1,
                        state = secondState,
                        modifier = Modifier.size(pickerW, picherH),
                        contentDescription = secondContentDescription,
                        option = { second: Int -> Option(1, "%03d".format(second)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = ".", style = textStyle, color = MaterialTheme.colors.onBackground)
                    Spacer(Modifier.width(8.dp))
                    Picker(
                        readOnly = selectedColumn != 2,
                        state = thirdState,
                        modifier = Modifier.size(pickerW, picherH),
                        contentDescription = thirdContentDescription,
                        option = { third: Int -> Option(2, "%03d".format(third)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = ".", style = textStyle, color = MaterialTheme.colors.onBackground)
                    Spacer(Modifier.width(8.dp))
                    Picker(
                        readOnly = selectedColumn != 3,
                        state = fourthState,
                        modifier = Modifier.size(pickerW, picherH),
                        contentDescription = fourthContentDescription,
                        option = { fourth: Int -> Option(3, "%03d".format(fourth)) }
                    )
                }
            }
        }
        item {
            DefaultHeadline(text = "Set Hand Mode")
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
            }
        }

        item {
            SmallCard() {
                var expanded by remember { mutableStateOf(false) }

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
                        "${firstState.selectedOption}." +
                                "${secondState.selectedOption}." +
                                "${thirdState.selectedOption}." +
                                "${fourthState.selectedOption}",
                        leftHandMode,
                        recordLocally
                    )
                },
                text = "Done"
            )
        }
    }
}
