package com.mocap.phone.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mocap.phone.DataSingleton

/**
 * Displays all the main functions
 */
@Composable
fun RenderHome(pingCallback: () -> Unit) {

    val lastPing by DataSingleton.lastPingResponse.collectAsState()


    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.background)
    ) {
        item {
            Text(
                text = "Hello",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "Last Ping: $lastPing",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Button(
                onClick = { pingCallback() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Ping Watch")
            }
        }
    }
}