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
fun RenderWatchOnlyUdp(
    audioStreamStateFlow: StateFlow<AudioStreamState>,
    imuStreamStateFlow: StateFlow<ImuStreamState>,
    calibrated: StateFlow<Boolean>,
    gravDiff: StateFlow<Float>,
    imuStreamCallback: (Boolean) -> Unit,
    audioStreamCallback: (Boolean) -> Unit,
    ipSetCallback: () -> Unit,
    finishCallback: () -> Unit
) {

    val imuState by imuStreamStateFlow.collectAsState()
    val audioState by audioStreamStateFlow.collectAsState()
    val gravNum by gravDiff.collectAsState()
    val cal by calibrated.collectAsState()
    val ip by DataSingleton.ip.collectAsState()

    // display information in a column
    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        // calibration
        item {
            Text(
                text = if (cal) "Posture Good" else "Hold watch level to ground and parallel to Hip\n" +
                        "%.2f".format(gravNum),
                color = if (cal) Color.Green else Color.Red,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1
            )
        }
        item {
            StreamToggle(
                enabled = cal or (imuState == ImuStreamState.Streaming),
                text = "Stream IMU",
                checked = (imuState == ImuStreamState.Streaming),
                onChecked = { imuStreamCallback(it) }
            )
        }
        item {
            StreamToggle(
                enabled = true,
                text = "Stream Audio",
                checked = (audioState == AudioStreamState.Streaming),
                onChecked = { audioStreamCallback(it) }
            )
        }
        item {
            DefaultText(text = ip)
        }
        item {
            DefaultButton(
                enabled = (imuState != ImuStreamState.Streaming) &&
                        (audioState != AudioStreamState.Streaming),
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