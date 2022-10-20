package com.example.sensorrecord.presentation

import android.hardware.SensorManager
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

// for logging
private const val TAG = "SensorViewModel"

enum class STATE {
    ready, // app waits for user to trigger the recording
    recording, // recording sensor data into memory
    processing, // processing sensor data from memory into CSV. When finished, back to WAITING
    error // error state. Stop using the app
}

/**
 * This App follows the unidirectional data flow design pattern of the Jetpack Compose UI.
 * We keep the permanent state of the screen in this ViewModel.
 * It exposes the state as FlowData, which the view "observes" and reacts to.
 * The state is altered via callbacks (Events).
 */
class SensorViewModel : ViewModel() {
    // State
    // A change in MutableStateFlow values triggers a redraw of elements that use it
    private val _startTimeStamp = MutableStateFlow<LocalDateTime>(LocalDateTime.now())
    val startTimeStamp = _startTimeStamp.asStateFlow()

    private val _currentState = MutableStateFlow(STATE.ready)
    val currentState = _currentState.asStateFlow()

    // Internal sensor reads that get updated as fast as possible
    private var rotVec: FloatArray = FloatArray(3) // Rotation Vector sensor or estimation
    private var lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var accl: FloatArray = FloatArray(3) // raw acceleration
    private var gyro: FloatArray = FloatArray(3) // gyroscope
    private var magn: FloatArray = FloatArray(3) // magnetic
    private var grav: FloatArray = FloatArray(3) // gravity

    private var data: ArrayList<FloatArray> = ArrayList() // all recorded data


    // Events
    /**
     * Sensors are triggered in different frequencies and might have varying delays.
     * To fetch all data at the same time, this function is called in fixed timed intervals
     * by Timer in MainActivity.kt
     */
    fun recordSensorValues(secTime: Long) {
        // only record observations if the switch was turned on
        if (_currentState.value == STATE.recording) {
            // write to data
            data.add(
                floatArrayOf(
                    rotVec[0], // rotation vector euler x
                    rotVec[1], // y
                    rotVec[2], // z
                    lacc[0], // linear acceleration x
                    lacc[1], // y
                    lacc[2], // z
                    pres[0], // atmospheric pressure
                    accl[0], // unfiltered acceleration x
                    accl[1], // y
                    accl[2], // z
                    gyro[0], // Angular speed around the x-axis
                    gyro[1], // y
                    gyro[2], // z
                    magn[0], // the ambient magnetic field in the x-axis
                    magn[1], // y
                    magn[2], // z
                    grav[0], // vector indicating the direction and magnitude of gravity X
                    grav[1], // y
                    grav[2] // z
                )
            )
        }
    }

    // Individual sensor reads are triggered by their onValueChanged events
    fun onLaccReadout(newReadout: FloatArray) {
        lacc = newReadout
    }

    fun onRotVecReadout(newReadout: FloatArray) {
        rotVec = newReadout
    }

    fun onAcclReadout(newReadout: FloatArray) {
        accl = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        grav = newReadout
    }

    fun onGyroReadout(newReadout: FloatArray) {
        gyro = newReadout
    }

    fun onPressureReadout(newReadout: FloatArray) {
        pres = newReadout
    }

    fun onMagnReadout(newReadout: FloatArray) {
        magn = newReadout
    }

    // changes with the ClipToggle
    fun recordTrigger(checked: Boolean) {
        // no actions allowed if not in ready or recording state
        if (_currentState.value != STATE.ready && _currentState.value != STATE.recording) {
            Log.v(TAG, "still processing previous recording - cannot record yet")
            return
        }

        if (checked) {
            //if turned on, record exact start time
            _currentState.value = STATE.recording
            _startTimeStamp.value = LocalDateTime.now()
        } else {
            // if turned off, safe data an clear
            _currentState.value = STATE.processing
            // data processing in separate thread to not jack the UI
            thread {
                // next state determined by whether data processing was successful 
                _currentState.value = saveToDatedCSV(_startTimeStamp.value, data)
                data.clear()
            }
        }
    }
}

/**
 * Parses data from SensorViewModel into a CSV with header and stores it into the public shared
 * Documents folder of the Android device that runs this app. The file name uses the input LocalDateTime
 * for a unique name.
 */
fun saveToDatedCSV(start: LocalDateTime, data: java.util.ArrayList<FloatArray>): STATE {

    // create unique filename from current date and time
    val currentDate = (DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")).format(start)
    val fileName = "sensorrecord_${currentDate}.csv"

    // permission rules only allow to write into the public shared directory
    // /storage/emulated/0/Documents/_2022-09-273_05-08-49.csv
    val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    val textFile = File(path, fileName)

    // try to write data into a file at above location
    try {
        val fOut = FileWriter(textFile)
        // write header
        fOut.write(
            "millisec, " +
                    "rot_x, rot_y, rot_z, " +
                    "lacc_x, lacc_y, lacc_z, " +
                    "pres, " +
                    "accl_x, accl_y, accl_z, " +
                    "gyro_x, gyro_y, gyro_z, " +
                    "magn_x, magn_y, magn_z, " +
                    "grav_x, grav_y, grav_z \n"
        )
        // write row-by-row
        for (arr in data) {
            fOut.write("%d,".format(arr[0].toInt())) // milliseconds as integer
            // round all floats to .4 precision
            for (entry in arr.slice(1 until arr.size - 1)) {
                fOut.write("%.4f,".format(entry))
            }
            fOut.write("%.4f\n".format(arr[arr.size - 1])) // new line at the end
        }
        fOut.flush()
        fOut.close()
    } catch (e: IOException) {
        e.printStackTrace()
        println("Log file creation failed.")
        return STATE.error
    }


    // Parse the file and path to uri
    println("Text file created at ${textFile.absolutePath}.")
    return STATE.ready
}