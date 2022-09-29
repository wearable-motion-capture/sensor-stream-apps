package com.example.android.wearable.sensorrecord

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


/**
 * This App follows the unidirectional data flow design pattern with the new Jetpack Compose UI world.
 * It keeps the permanent state of the screen in this ViewModel.
 * It exposes that state as FlowData that the view "observes" and reacts to.
 */
class SensorViewModel : ViewModel() {
    // State
    // A change in MutableStateFlow values triggers a redraw of elements that use it
    private val _accReadout = MutableStateFlow("")
    val accReadout = _accReadout.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()


    // Events
    fun onAccSensorReadout(newReadout: FloatArray) {
        // only record if the toggle chip was turned on
        if (_recording.value) {
            _accReadout.value =
                String.format("%.1f %.1f %.1f", newReadout[0], newReadout[1], newReadout[2])
        }
    }

    fun recordSwitch(state: Boolean) {
        _recording.value = state
    }
}