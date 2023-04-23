package com.mocap.phone.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mocap.phone.modules.PingRequester

/**
 * Displays all the main functions
 */
@Composable
fun RenderHome(pingRequester: PingRequester) {

    LazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text(
                text = "Hello",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Button(
                onClick = { pingRequester.requestPing() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Ping Watch")
            }
        }
    }
}