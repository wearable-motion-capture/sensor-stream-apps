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
import com.mocap.watch.ImuStreamState
import com.mocap.watch.AudioStreamState
import com.mocap.watch.ui.DefaultButton
import com.mocap.watch.ui.DefaultText
import com.mocap.watch.ui.RedButton
import com.mocap.watch.ui.StreamToggle

@Composable
fun RenderStandalone(
    soundStateFlow: StateFlow<AudioStreamState>,
    sensorStateFlow: StateFlow<ImuStreamState>,
    calibCallback: () -> Unit,
    imuStreamCallback: (Boolean) -> Unit,
    micStreamCallback: (Boolean) -> Unit,
    ipSetCallback: () -> Unit,
    finishCallback: () -> Unit
) {

    val sensorState by sensorStateFlow.collectAsState()
    val soundState by soundStateFlow.collectAsState()
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
                enabled = (sensorState == ImuStreamState.Idle),
                onClick = { calibCallback() },
                text = "Recalibrate"
            )
        }
        // Sensor data handler
//        item {
//            StreamToggle(
//                enabled = (sensorState == SensorStreamState.Idle) or
//                        (sensorState == SensorStreamState.Recording),
//                text = "Record Locally",
//                checked = (sensorState == SensorStreamState.Recording),
//                onChecked = { recordCallback(it) }
//            )
//        }
        item {
            SensorStateDisplay(state = sensorState, modifier = Modifier.fillMaxWidth())
        }
        item {
            StreamToggle(
                enabled = true,
                text = "Stream IMU",
                checked = (sensorState == ImuStreamState.Streaming),
                onChecked = { imuStreamCallback(it) }
            )
        }
        item {
            SoundStateDisplay(state = soundState, modifier = Modifier.fillMaxWidth())
        }
        item {
            StreamToggle(
                enabled = true,
                text = "Stream Audio",
                checked = (soundState == AudioStreamState.Streaming),
                onChecked = { micStreamCallback(it) }
            )
        }
        item {
            DefaultText(text = ip)
        }
        item {
            DefaultButton(
                enabled = (sensorState != ImuStreamState.Streaming) &&
                        (soundState != AudioStreamState.Streaming),
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
 * ready, streaming, Error
 */
@Composable
fun SensorStateDisplay(state: ImuStreamState, modifier: Modifier = Modifier) {
    var color = Color.Red
    if (state == ImuStreamState.Idle) {
        color = Color.Yellow
    } else if (state == ImuStreamState.Streaming) {
        color = Color.Green
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


/**
 * The simple state display when recording or streaming data. Switches between:
 * ready, streaming, Error
 */
@Composable
fun SoundStateDisplay(state: AudioStreamState, modifier: Modifier = Modifier) {
    var color = Color.Red
    if (state == AudioStreamState.Idle) {
        color = Color.Yellow
    } else if (state == AudioStreamState.Streaming) {
        color = Color.Green
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