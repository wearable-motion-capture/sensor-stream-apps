package com.mocap.phone.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.mocap.phone.DataSingleton
import com.mocap.phone.ui.DefaultButton
import com.mocap.phone.ui.DefaultText
import kotlinx.coroutines.flow.StateFlow

/**
 * Displays all the main functions
 */
@Composable
fun RenderHome(
    connectedNodeStateFlow: StateFlow<String>,
    appActiveStateFlow: StateFlow<Boolean>
) {
    val phoneForwardQuat by DataSingleton.phoneQuat.collectAsState()
    val watchForwardQuat by DataSingleton.watchQuat.collectAsState()
    val watchPressure by DataSingleton.watchPres.collectAsState()

    val nodeName by connectedNodeStateFlow.collectAsState()
    val appState by appActiveStateFlow.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        item {
            DefaultText(text = "Connected: $nodeName")
        }
        item {
            Text(
                text = if (appState) "Watch Dual App Active" else "Watch Dual App Inactive",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = if (appState) Color.Green else Color.Red
            )
        }
        item {
            DefaultText(
                text = ("Phone Forward:\n" +
                        "%.2f, %.2f, %.2f, %.2f").format(
                    phoneForwardQuat[0],
                    phoneForwardQuat[1],
                    phoneForwardQuat[2],
                    phoneForwardQuat[3]
                )
            )
        }
        item {
            DefaultText(
                text = ("Watch Forward:\n" +
                        "%.2f, %.2f, %.2f, %.2f").format(
                    watchForwardQuat[0],
                    watchForwardQuat[1],
                    watchForwardQuat[2],
                    watchForwardQuat[3]
                )
            )
        }
        item {
            DefaultText(
                text = ("Watch Pressure:\n %.2f").format(watchPressure)
            )
        }

    }
}