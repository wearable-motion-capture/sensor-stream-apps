package com.mocap.watch.ui.view

import android.Manifest
import androidx.annotation.RequiresPermission

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text

import com.mocap.watch.modules.SoundStreamer
import com.mocap.watch.modules.SensorCalibrator
import com.mocap.watch.modules.SensorDataHandler

import com.mocap.watch.GlobalState
import com.mocap.watch.Views
import com.mocap.watch.SensorDataHandlerState
import com.mocap.watch.SoundStreamState
import com.mocap.watch.modules.PingRequester
import com.mocap.watch.modules.PingResponder

import com.mocap.watch.ui.DataStateDisplay
import com.mocap.watch.ui.SensorToggleChip

@Composable
@RequiresPermission(Manifest.permission.RECORD_AUDIO)
fun RenderHome(
    globalState: GlobalState,
    sensorDataHandler: SensorDataHandler,
    soundStreamer: SoundStreamer,
    calibrator: SensorCalibrator,
    pingRequester: PingRequester
) {

    // get the information to display from the global state
    val soundState by globalState.soundStrState.collectAsState()
    val sensorState by globalState.sensorStrState.collectAsState()
    val initPres by calibrator.initPres.collectAsState()
    val northDeg by calibrator.northDeg.collectAsState()

    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Button(
                onClick = { pingRequester.requestPing() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Ping Phone")
            }
        }
        item {
            Text(
                text = "pres: ${"%.2f".format(initPres)} deg: ${"%.2f".format(northDeg)}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Button(
                enabled = (sensorState == SensorDataHandlerState.Idle),
                onClick = { globalState.setView(Views.Calibration) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Recalibrate")
            }
        }
        item {
            SensorToggleChip(
                enabled = (sensorState == SensorDataHandlerState.Idle) or
                        (sensorState == SensorDataHandlerState.Recording),
                text = "Record Locally",
                checked = (sensorState == SensorDataHandlerState.Recording),
                onChecked = { sensorDataHandler.recordTrigger(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            DataStateDisplay(state = sensorState, modifier = Modifier.fillMaxWidth())
        }
        item {
            SensorToggleChip(
                enabled = (sensorState == SensorDataHandlerState.Idle) or
                        (sensorState == SensorDataHandlerState.Streaming),
                text = "Stream to IP",
                checked = (sensorState == SensorDataHandlerState.Streaming),
                onChecked = { sensorDataHandler.triggerImuStreamUdp(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            SensorToggleChip(
                enabled = true,
                text = "Stream MIC",
                checked = (soundState == SoundStreamState.Streaming),
                onChecked = { soundStreamer.triggerMicStream(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                text = globalState.getIP(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Button(
                enabled = (sensorState == SensorDataHandlerState.Idle) &&
                        (soundState == SoundStreamState.Idle),
                onClick = { globalState.setView(Views.IPsetting) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Set Target IP")
            }
        }
        item {
            Text("app version " + globalState.getVersion())
        }
    }
}