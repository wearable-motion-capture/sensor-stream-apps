package com.example.sensorrecord.presentation

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
import kotlin.collections.ArrayList


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

    private val _recordingT = MutableStateFlow(false)
    val recordingTrigger = _recordingT.asStateFlow()

    private val _startTS = MutableStateFlow<LocalDateTime>(LocalDateTime.now())
    val startTimeStamp = _startTS.asStateFlow()

    // Internal sensor reads that get updated as fast as possible
    private var grav: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var gyro: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var accl: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var data: ArrayList<FloatArray> = ArrayList()


    // Events
    /**
     * Sensors are triggered in different frequencies and might have varying delays.
     * To fetch all data at the same time, this function is called in fixed timed intervals
     * by Timer in MainActivity.kt
     */
    fun timedSensorValues(secTime: Long) {
        // only record observations if the switch was turned on
        if (_recordingT.value) {
            _accReadout.value =
                String.format("%.1f %.1f %.1f", accl[0], accl[1], accl[2])
            _gravReadout.value =
                String.format("%.1f %.1f %.1f", grav[0], grav[1], grav[2])
            _gyroReadout.value =
                String.format("%.1f %.1f %.1f", gyro[0], gyro[1], gyro[2])
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
        _recordingT.value = state
        //if turned on, record exact start time
        if (state) {
            _startTS.value = LocalDateTime.now()
        }
        // if turned off, safe data an clear
        if (!state) {
            saveToDatedCSV(_startTS.value, data)
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