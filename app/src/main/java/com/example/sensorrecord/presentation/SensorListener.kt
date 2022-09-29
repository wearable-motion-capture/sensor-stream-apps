package com.example.sensorrecord.presentation

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

/**
 * As per the unidirectional data flow design pattern, events flow up. Thus, this listener calls
 * the onReadout lambda function when onSensorChanged is triggered.
 */
class SensorListener(val onReadout: (FloatArray) -> Unit) : SensorEventListener {

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // method to check accuracy changed in sensor.
    }

    // we get the data in through the on sensor changed trigger
    override fun onSensorChanged(event: SensorEvent) {
        onReadout(event.values)
    }
}
