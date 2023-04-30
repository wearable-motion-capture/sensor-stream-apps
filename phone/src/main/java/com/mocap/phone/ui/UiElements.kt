package com.mocap.phone.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun DefaultText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onBackground,
        style = MaterialTheme.typography.body1
    )
}

@Composable
fun DefaultButton(enabled: Boolean = true, onClick: () -> Unit, text: String) {
    Button(
        enabled = enabled,
        onClick = { onClick() },
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Text(text = text)
    }
}

@Composable
fun BigCard(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    content: @Composable () -> Unit
) {
    Card(modifier = modifier.padding(8.dp), elevation = 4.dp, backgroundColor = color) { content() }
}

@Composable
fun SmallCard(
    modifier: Modifier = Modifier,
    color: Color = Color.DarkGray,
    content: @Composable () -> Unit
) {
    Card(modifier = modifier.padding(8.dp), backgroundColor = color) { content() }
}