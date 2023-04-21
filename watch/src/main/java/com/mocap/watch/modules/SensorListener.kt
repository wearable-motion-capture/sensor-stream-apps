package com.mocap.watch.modules

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log

/**
 * A simple class to register a listener for sensors. The code variable is a Sensor number
 * (e.g. TYPE_GYROSCOPE)
 * As per the unidirectional data flow design pattern, events flow up. Thus, this listener calls
 * the input onReadout lambda function when onSensorChanged is triggered.
 */
class SensorListener(val code: Int, val onReadout: (FloatArray) -> Unit) : SensorEventListener {

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // method to check accuracy changed in sensor.
    }

    // we get the data in through the on sensor changed trigger
    override fun onSensorChanged(event: SensorEvent) {
        onReadout(event.values)
    }
}

/**
 * A debug listener simply logs the values when onSensorChanged is triggered. This is only for
 * testing purposes.
 */
class DebugSensorListener(val code: Int) :
    SensorEventListener {

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // method to check accuracy changed in sensor.
    }

    // we get the data in through the on sensor changed trigger
    override fun onSensorChanged(event: SensorEvent) {
        var msgstr = "["
        for (va in event.values) {
            msgstr += va.toString() + " ,"
        }
        msgstr += "]"
        Log.v("DebugSensorListener", code.toString() + " : " + msgstr)
    }
}

