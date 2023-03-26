package com.example.sensorrecord.presentation.ui

import android.view.MotionEvent
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
import androidx.compose.runtime.setValue // only if using var

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.style.TextAlign
import com.example.sensorrecord.presentation.modules.GlobalState
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IpSettingUi(globalState: GlobalState) {


    val ipStr = globalState.getIP().split(".")

    var selectedColumn by remember { mutableStateOf(0) }
    val textStyle = MaterialTheme.typography.body1

    val firstState =
        rememberPickerState(
            initialNumberOfOptions = 1000,
            initiallySelectedOption = ipStr[0].toInt()
        )
    val firstContentDescription by remember {
        derivedStateOf { "${firstState.selectedOption}" }
    }
    val secondState =
        rememberPickerState(
            initialNumberOfOptions = 1000,
            initiallySelectedOption = ipStr[1].toInt()
        )
    val secondContentDescription by remember {
        derivedStateOf { "${secondState.selectedOption}" }
    }
    val thirdState =
        rememberPickerState(
            initialNumberOfOptions = 1000,
            initiallySelectedOption = ipStr[2].toInt()
        )
    val thirdContentDescription by remember {
        derivedStateOf { "${thirdState.selectedOption}" }
    }
    val fourthState =
        rememberPickerState(
            initialNumberOfOptions = 1000,
            initiallySelectedOption = ipStr[3].toInt()
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
                    if (it.action == MotionEvent.ACTION_DOWN) selectedColumn = column
                    true
                }
        )
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        autoCentering = AutoCenteringParams(itemIndex = 1),
        userScrollEnabled = false
    ) {
        item {
            Text(text = "Set Local IP", textAlign = TextAlign.Center)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
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

        item {
            Button(
                onClick = {
                    globalState.setIP(
                        "${firstState.selectedOption}." +
                                "${secondState.selectedOption}." +
                                "${thirdState.selectedOption}." +
                                "${fourthState.selectedOption}"
                    )
                }
            ) {
                Text(text = "Done")
            }
        }
    }
}