package com.mocap.phone.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mocap.phone.ui.DefaultText
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderCalib(
    quatReadingStateFlow: StateFlow<FloatArray>
) {

    val curQuat by quatReadingStateFlow.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.background)
    ) {
        item {
            DefaultText(text = "Performing Calibration")
        }
        item {
            DefaultText(
                text = "Phone Forward:\n%.2f, %.2f, %.2f, %.2f".format(
                    curQuat[0],
                    curQuat[1],
                    curQuat[2],
                    curQuat[3]
                )
            )
        }
    }
}