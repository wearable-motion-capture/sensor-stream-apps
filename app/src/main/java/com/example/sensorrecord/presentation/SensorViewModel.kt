package com.example.sensorrecord.presentation

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

    private val _gravReadout = MutableStateFlow("")
    val gravReadout = _gravReadout.asStateFlow()

    private val _gyroReadout = MutableStateFlow("")
    val gyroReadout = _gyroReadout.asStateFlow()

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

    fun onGravSensorReadout(newReadout: FloatArray) {
        // only record if the toggle chip was turned on
        if (_recording.value) {
            _gravReadout.value =
                String.format("%.1f %.1f %.1f", newReadout[0], newReadout[1], newReadout[2])
        }
    }

    fun onGyroSensorReadout(newReadout: FloatArray) {
        // only record if the toggle chip was turned on
        if (_recording.value) {
            _gyroReadout.value =
                String.format("%.1f %.1f %.1f", newReadout[0], newReadout[1], newReadout[2])
        }
    }

    fun recordSwitch(state: Boolean) {
        _recording.value = state
    }
}