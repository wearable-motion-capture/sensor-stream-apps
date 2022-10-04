package com.example.sensorrecord.presentation

import android.hardware.SensorManager
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
 * This App follows the unidirectional data flow design pattern with the new Jetpack Compose UI world.
 * It keeps the permanent state of the screen in this ViewModel.
 * It exposes that state as FlowData that the view "observes" and reacts to.
 */
class SensorViewModel : ViewModel() {
    // State
    // A change in MutableStateFlow values triggers a redraw of elements that use it
    private val _recordingTrigger = MutableStateFlow(false)
    val recordingTrigger = _recordingTrigger.asStateFlow()

    private val _startTimeStamp = MutableStateFlow<LocalDateTime>(LocalDateTime.now())
    val startTimeStamp = _startTimeStamp.asStateFlow()

    private val _yaw = MutableStateFlow(0f)
    val yaw = _yaw.asStateFlow() // yaw, rotation around z axis
    private val _pit = MutableStateFlow(0f)
    val pitch = _pit.asStateFlow() // pitch, rotation around x axis
    private val _rol = MutableStateFlow(0f)
    val roll = _rol.asStateFlow() // roll, rotation around y axis


    // Internal sensor reads that get updated as fast as possible
    private var grav: FloatArray = FloatArray(3) // gravity
    private var magn: FloatArray = FloatArray(3) // magnetic
    private var gyro: FloatArray = FloatArray(3) // gyroscope
    private var accl: FloatArray = FloatArray(3) // raw acceleration
    private var orient: FloatArray = FloatArray(3) // estimated orientation
    private var rotMat: FloatArray = FloatArray(16) // estimated rotation matrix
    private var data: ArrayList<FloatArray> = ArrayList() // all recorded data


    // Events
    /**
     * Sensors are triggered in different frequencies and might have varying delays.
     * To fetch all data at the same time, this function is called in fixed timed intervals
     * by Timer in MainActivity.kt
     */
    fun timedSensorValues(secTime: Long) {
        // only record observations if the switch was turned on
        if (_recordingTrigger.value) {
            if (SensorManager.getRotationMatrix(rotMat, null, grav, magn)) {
                println("rot mat ${rotMat}")
                SensorManager.getOrientation(rotMat, orient)
                // 1 radian = 57.2957795 degrees
                _yaw.value = orient[0] * 57.29578f
                _pit.value = orient[1] * 57.29578f
                _rol.value = orient[2] * 57.29578f
            }
            data.add(
                floatArrayOf(
                    secTime.toFloat(),
                    accl[0], accl[1], accl[2],
                    grav[0], grav[1], grav[2],
                    gyro[0], gyro[1], gyro[2]
                )
            )
        }
    }

    // Individual sensor reads are triggered by their onValueChanged events
    fun onAcclSensorReadout(newReadout: FloatArray) {
        accl = newReadout
    }

    fun onGravSensorReadout(newReadout: FloatArray) {
        grav = newReadout
    }

    fun onGyroSensorReadout(newReadout: FloatArray) {
        gyro = newReadout
    }

    fun onMagnSensorReadout(newReadout: FloatArray) {
        magn = newReadout
    }

    // changes with the ClipToggle
    fun recordSwitch(state: Boolean) {
        _recordingTrigger.value = state
        //if turned on, record exact start time
        if (state) {
            _startTimeStamp.value = LocalDateTime.now()
        }
        // if turned off, safe data an clear
        if (!state) {
            saveToDatedCSV(_startTimeStamp.value, data)
            data.clear()
        }
    }
}


fun saveToDatedCSV(start: LocalDateTime, data: java.util.ArrayList<FloatArray>): Uri {

    // create filename from current date and time
    val currentDate = (DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")).format(start)
    val fileName = "sensorrecord_${currentDate}.csv"

    // most basic files directory
    // /storage/emulated/0/Documents/_2022-09-273_05-08-49.csv
    val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)


    val textFile = File(path, fileName)
    try {
        val fOut = FileWriter(textFile)
        for (arr in data) {
            fOut.write("${arr[0]},${arr[1]},${arr[2]}\n")
        }
        fOut.flush()
        fOut.close()
    } catch (e: IOException) {
        e.printStackTrace()
        println("Text file creation failed.")
    }


    // Parse the file and path to uri
    var sharedUri = Uri.parse(textFile.absolutePath)
    println("Text file created at $sharedUri.")
    return sharedUri
}