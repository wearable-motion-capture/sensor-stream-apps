package com.mocap.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun DefaultText(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onBackground,
        style = MaterialTheme.typography.body1
    )
}

@Composable
fun DefaultHighlight(text: String, color: Color = MaterialTheme.colors.secondary) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        color = color,
        style = MaterialTheme.typography.h6,
        modifier = Modifier.padding(4.dp)
    )
}

@Composable
fun DefaultHeadline(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onBackground,
        style = MaterialTheme.typography.h5,
        modifier = Modifier.padding(4.dp)
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
    color: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    Card(modifier = modifier.padding(8.dp).fillMaxWidth(), elevation = 4.dp, backgroundColor = color) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.padding(8.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SmallCard(
    modifier: Modifier = Modifier,
    color: Color = Color.DarkGray,
    content: @Composable () -> Unit
) {
    Card(modifier = modifier.padding(4.dp), backgroundColor = color) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.padding(12.dp)
        ) {
            content()
        }
    }
}