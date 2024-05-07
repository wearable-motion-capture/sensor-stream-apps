package com.mocap.watch.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mocap.watch.DataSingleton
import com.mocap.watch.ImuStreamState
import com.mocap.watch.ui.RedButton
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderSelfLabelling(
    connected: StateFlow<Boolean>,
    connectedNodeName: StateFlow<String>,
    imuStreamStateFlow: StateFlow<ImuStreamState>,
    curLabelSF: StateFlow<Int>,
    nxtLabelSF: StateFlow<Int>,
    finishCallback: () -> Unit
) {
//    val nodeName by connectedNodeName.collectAsState()
//    val con by connected.collectAsState()
    val streamSt by imuStreamStateFlow.collectAsState()
    val curLabel by curLabelSF.collectAsState()
    val nxtLabel by nxtLabelSF.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        userScrollEnabled = true
    ) {
        item {
            Text(
                text = if (streamSt == ImuStreamState.Streaming) " " else "STOPPED",
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (streamSt == ImuStreamState.Streaming) Color.Green else Color.Red
                    ),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )
        }
        item {
            if ((curLabel == -1) and (nxtLabel == 0)) {
                Text(
                    text = "Not Started Yet",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.display3
                )
            } else if ((curLabel == 0) and (nxtLabel == -1)) {
                Text(
                    text = "Press Button Again To Finish",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.display3
                )
            } else if ((curLabel == -1) and (nxtLabel == -1)) {
                Text(
                    text = "Done",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.display3
                )
            } else if (curLabel == 0) {
                Column {
                    Text(
                        text = "Press Button When Ready For:",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Magenta,
                        style = MaterialTheme.typography.title2
                    )
                    Text(
                        text = DataSingleton.activityLabels[nxtLabel],
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Magenta,
                        style = MaterialTheme.typography.display3
                    )
                }
            } else {
                Column {
                    Text(
                        text = "Current Activity: ",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Yellow,
                        style = MaterialTheme.typography.title2
                    )
                    Text(
                        text = DataSingleton.activityLabels[curLabel],
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Yellow,
                        style = MaterialTheme.typography.display3
                    )
                }
            }
        }
        item {
            Text(
                text = if (streamSt == ImuStreamState.Streaming) " " else "STOPPED",
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (streamSt == ImuStreamState.Streaming) Color.Green else Color.Red
                    ),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )
        }

//        item {
//            Text(
//                text = if (con) "Phone App Active\n$nodeName" else "Phone App Not Active",
//                color = if (con) Color.Green else Color.Red,
//                modifier = Modifier.fillMaxWidth(),
//                textAlign = TextAlign.Center,
//                style = MaterialTheme.typography.body1
//            )
//        }
//        item {
//            RedButton(
//                onClick = { finishCallback() },
//                text = "Exit"
//            )
//        }
    }
}