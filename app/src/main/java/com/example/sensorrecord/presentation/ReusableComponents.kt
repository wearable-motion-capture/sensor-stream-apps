package com.example.android.wearable.sensorrecord
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults

@Composable
fun ToggleChip(
    text : String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ToggleChip(
        modifier = modifier,
        checked = checked,
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.switchIcon(checked = checked),
                contentDescription = if (checked) "On" else "Off"
            )
        },
        onCheckedChange = onChecked,
        label = {
            Text(
                text = text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}



@Composable
fun TextDisplay(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        textAlign = TextAlign.Center,
        text = text,
    )
}