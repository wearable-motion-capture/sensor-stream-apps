package com.mocap.phone.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mocap.phone.ui.BigCard
import com.mocap.phone.ui.DefaultHeadline
import com.mocap.phone.ui.DefaultText
import com.mocap.phone.ui.SmallCard
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderCalib(
    quatReadingStateFlow: StateFlow<FloatArray>
) {

    val curQuat by quatReadingStateFlow.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        item {
            BigCard() {

                DefaultHeadline(text = "Performing Calibration")

                SmallCard() {
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
    }
}