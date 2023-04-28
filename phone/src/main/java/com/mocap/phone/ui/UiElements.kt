package com.mocap.phone.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

@Composable
fun DefaultText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onBackground
    )
}

@Composable
fun DefaultButton(enabled: Boolean = true, onClick: () -> Unit, text: String) {
    Button(
        enabled = enabled,
        onClick = { onClick() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = text)
    }
}

@Composable
fun RedButton(onClick: () -> Unit, text: String) {
    Button(
        onClick = { onClick() },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
    ) {
        Text(text = text)
    }

}