package com.example.sensorrecord.presentation

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
import androidx.wear.compose.material.*
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
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerState

@Composable
fun PickerTest() {
    val pickerGroupState = rememberPickerGroupState()
    val pickerStateHour = rememberPickerState(initialNumberOfOptions = 100)
    val pickerStateMinute = rememberPickerState(initialNumberOfOptions = 100)
    val pickerStateSeconds = rememberPickerState(initialNumberOfOptions = 100)
    val pickerStateMilliSeconds = rememberPickerState(initialNumberOfOptions = 100)

    val pickerWidth = 30.dp
    val pickerHeight = 30.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val headingText = mapOf(
            0 to "First", 1 to "Second", 2 to "Third", 3 to "Fourth"
        )
        Spacer(modifier = Modifier.size(30.dp))
        Text(text = headingText[pickerGroupState.selectedIndex]!!)
        Spacer(modifier = Modifier.size(10.dp))
        PickerGroup(
            PickerGroupItem(
                pickerState = pickerStateHour,
                option = { optionIndex, _ -> Text(text = "%02d".format(optionIndex)) },
                modifier = Modifier.size(pickerWidth, pickerHeight)
            ),
            PickerGroupItem(
                pickerState = pickerStateMinute,
                option = { optionIndex, _ -> Text(text = "%02d".format(optionIndex)) },
                modifier = Modifier.size(pickerWidth, pickerHeight)
            ),
            PickerGroupItem(
                pickerState = pickerStateSeconds,
                option = { optionIndex, _ -> Text(text = "%02d".format(optionIndex)) },
                modifier = Modifier.size(pickerWidth, pickerHeight)
            ),
            PickerGroupItem(
                pickerState = pickerStateMilliSeconds,
                option = { optionIndex, _ -> Text(text = "%03d".format(optionIndex)) },
                modifier = Modifier.size(pickerWidth, pickerHeight)
            ),
            pickerGroupState = pickerGroupState,
            autoCenter = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PickerTest2() {


    var selectedColumn by remember { mutableStateOf(0) }
    val textStyle = MaterialTheme.typography.display1

    @Composable
    fun Option(column: Int, text: String) = Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text, style = textStyle,
            color = if (selectedColumn == column) MaterialTheme.colors.secondary
            else MaterialTheme.colors.onBackground,
            modifier = Modifier
                .align(Alignment.Center).wrapContentSize()
                .pointerInteropFilter {
                    if (it.action == MotionEvent.ACTION_DOWN) selectedColumn = column
                    true
                }
        )
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val hourState = rememberPickerState(
            initialNumberOfOptions = 12,
            initiallySelectedOption = 5
        )
        val hourContentDescription by remember {
            derivedStateOf { "${hourState.selectedOption + 1 } hours" }
        }
        Picker(
            readOnly = selectedColumn != 0,
            state = hourState,
            modifier = Modifier.size(64.dp, 100.dp),
            contentDescription = hourContentDescription,
            option = { hour: Int -> Option(0, "%2d".format(hour + 1)) }
        )
        Spacer(Modifier.width(8.dp))
        Text(text = ":", style = textStyle, color = MaterialTheme.colors.onBackground)
        Spacer(Modifier.width(8.dp))
        val minuteState =
            rememberPickerState(initialNumberOfOptions = 60, initiallySelectedOption = 0)
        val minuteContentDescription by remember {
            derivedStateOf { "${minuteState.selectedOption} minutes" }
        }
        Picker(
            readOnly = selectedColumn != 1,
            state = minuteState,
            modifier = Modifier.size(64.dp, 100.dp),
            contentDescription = minuteContentDescription,
            option = { minute: Int -> Option(1, "%02d".format(minute)) }
        )
    }
}