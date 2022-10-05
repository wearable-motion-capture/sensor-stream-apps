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
 * This App follows the unidirectional data flow design pattern of the Jetpack Compose UI.
 * We keep the permanent state of the screen in this ViewModel.
 * It exposes the state as FlowData, which the view "observes" and reacts to.
 * The state is altered via callbacks (Events).
 */
class SensorViewModel : ViewModel() {
    // State
    // A change in MutableStateFlow values triggers a redraw of elements that use it
    private val _recordingTrigger = MutableStateFlow(false)
    val recordingTrigger = _recordingTrigger.asStateFlow()

    private val _startTimeStamp = MutableStateFlow<LocalDateTime>(LocalDateTime.now())
    val startTimeStamp = _startTimeStamp.asStateFlow()

    private val _lacc = MutableStateFlow("acc 0, 0, 0")
    val linearAcc = _lacc.asStateFlow()

    // Internal sensor reads that get updated as fast as possible
    private var grav: FloatArray = FloatArray(3) // gravity
    private var lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var magn: FloatArray = FloatArray(3) // magnetic
    private var gyro: FloatArray = FloatArray(3) // gyroscope
    private var accl: FloatArray = FloatArray(3) // raw acceleration
    private var orient: FloatArray = FloatArray(3) // estimated orientation
    private var rotVec: FloatArray = FloatArray(3) // Rotation Vector sensor or estimation
    private var rotMat: FloatArray = FloatArray(9) // estimated rotation matrix
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
            // update linear acceleration state flow
            _lacc.value = "acc %.1f, %.1f, %.1f".format(lacc[0], lacc[1], lacc[2])
            // update rotation matrix
            SensorManager.getRotationMatrix(rotMat, null, accl, magn)
            // write to data
            data.add(
                floatArrayOf(
                    secTime.toFloat(),
                    rotMat[0], rotMat[1], rotMat[2],
                    rotMat[3], rotMat[4], rotMat[5],
                    rotMat[6], rotMat[7], rotMat[8],
                    lacc[0], lacc[1], lacc[2]
                )
            )
        }
    }

    // Individual sensor reads are triggered by their onValueChanged events
    fun onAcclReadout(newReadout: FloatArray) {
        accl = newReadout
    }

    fun onLaccReadout(newReadout: FloatArray) {
        lacc = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        grav = newReadout
    }

    fun onGyroReadout(newReadout: FloatArray) {
        gyro = newReadout
    }

    fun onMagnReadout(newReadout: FloatArray) {
        magn = newReadout
    }

    fun onRotVecReadout(newReadout: FloatArray) {
        rotVec = newReadout
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
            //saveToDatedCSV(_startTimeStamp.value, data)
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