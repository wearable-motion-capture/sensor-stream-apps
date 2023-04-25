import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text

import kotlinx.coroutines.flow.StateFlow

import com.mocap.watch.DataSingleton
import com.mocap.watch.stateModules.SensorDataHandlerState
import com.mocap.watch.stateModules.AudioModuleState
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.DefaultText
import com.mocap.watch.ui.RedButton
import com.mocap.watch.ui.SensorToggleChip

@Composable
fun RenderStandAlone(
    soundStateFlow: StateFlow<AudioModuleState>,
    sensorStateFlow: StateFlow<SensorDataHandlerState>,
    calibCallback: () -> Unit,
    recordCallback: (Boolean) -> Unit,
    imuStreamCallback: (Boolean) -> Unit,
    micStreamCallback: (Boolean) -> Unit,
    ipSetCallback: () -> Unit,
    finishCallback: () -> Unit
) {

    val audioState by soundStateFlow.collectAsState()
    val sensorState by sensorStateFlow.collectAsState()
    val ip by DataSingleton.IP.collectAsState()
    val north by DataSingleton.CALIB_NORTH.collectAsState()
    val press by DataSingleton.CALIB_PRESS.collectAsState()

    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        // calibration
        item {
            DefaultText(
                text = "pres: ${"%.2f".format(press)} " +
                        "deg: ${"%.2f".format(north)}"
            )
        }
        item {
            DefaultButton(
                enabled = (sensorState == SensorDataHandlerState.Idle),
                onClick = { calibCallback() },
                text = "Recalibrate"
            )
        }
        // Sensor data handler
        item {
            SensorToggleChip(
                enabled = (sensorState == SensorDataHandlerState.Idle) or
                        (sensorState == SensorDataHandlerState.Recording),
                text = "Record Locally",
                checked = (sensorState == SensorDataHandlerState.Recording),
                onChecked = { recordCallback(it) },
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
                onChecked = { imuStreamCallback(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            SensorToggleChip(
                enabled = true,
                text = "Stream MIC",
                checked = (audioState == AudioModuleState.Streaming),
                onChecked = { micStreamCallback(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            DefaultText(text = ip)
        }
        item {
            DefaultButton(
                enabled = (sensorState == SensorDataHandlerState.Idle) &&
                        (audioState == AudioModuleState.Idle),
                onClick = { ipSetCallback() },
                text = "Set Target IP"
            )
        }
        item {
            RedButton(
                onClick = { finishCallback() },
                text = "Change Mode"
            )
        }
    }
}

/**
 * The simple state display when recording or streaming data. Switches between:
 * ready, recording/streaming, processing
 */
@Composable
fun DataStateDisplay(state: SensorDataHandlerState, modifier: Modifier = Modifier) {
    var color = Color.Red
    if (state == SensorDataHandlerState.Idle) {
        color = Color.Green
    } else if (state == SensorDataHandlerState.Processing) {
        color = Color.Yellow
    }
    Text(
        modifier = modifier,
        textAlign = TextAlign.Center,
        text = state.name,
        style = MaterialTheme.typography.body1.copy(
            color = color
        )
    )
}