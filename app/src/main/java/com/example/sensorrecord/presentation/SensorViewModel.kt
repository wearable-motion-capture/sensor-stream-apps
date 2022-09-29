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
    private val _accReadout = MutableStateFlow("0.0 0.0 0.0")
    val accReadout = _accReadout.asStateFlow()

    private val _gravReadout = MutableStateFlow("0.0 0.0 0.0")
    val gravReadout = _gravReadout.asStateFlow()

    private val _gyroReadout = MutableStateFlow("0.0 0.0 0.0")
    val gyroReadout = _gyroReadout.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()

    // Internal sensor reads that get updated as fast as possible
    private var grav: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var gyro: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var accl: FloatArray = floatArrayOf(0f, 0f, 0f)


    // Events
    /**
     * Sensors are triggered in different frequencies and might have varying delays.
     * To fetch all data at the same time, this function is called in fixed timed intervals
     * by Timer in MainActivity.kt
     */
    fun timedSensorValues(secTime: Long) {
        if (_recording.value) {
            _accReadout.value =
                String.format("%.1f %.1f %.1f", accl[0], accl[1], accl[2])
            _gravReadout.value =
                String.format("%.1f %.1f %.1f", grav[0], grav[1], grav[2])
            _gyroReadout.value =
                String.format("%.1f %.1f %.1f", gyro[0], gyro[1], gyro[2])
        }
    }

    // Individual sensor reads are triggered by their onValueChanged events
    fun onAccSensorReadout(newReadout: FloatArray) {
        accl = newReadout
    }

    fun onGravSensorReadout(newReadout: FloatArray) {
        grav = newReadout
    }

    fun onGyroSensorReadout(newReadout: FloatArray) {
        gyro = newReadout
    }

    // changes with the ClipToggle
    fun recordSwitch(state: Boolean) {
        _recording.value = state
    }
}