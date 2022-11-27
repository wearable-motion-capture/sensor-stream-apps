package com.example.sensorrecord.presentation

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
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.net.*
// for logging
private const val TAG = "SensorViewModel"

enum class STATE {
    ready, // app waits for user to trigger the recording
    recording, // recording sensor data into memory
    processing, // processing sensor data from memory into CSV. When finished, back to WAITING
    error, // error state. Stop using the app,
    stream
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
    var tcp_client: Socket=  Socket()

    private var th = thread {  }
    private val _currentState = MutableStateFlow(STATE.ready)
    val currentState = _currentState.asStateFlow()
    // Internal sensor reads that get updated as fast as possible
    private var rotVec: FloatArray = FloatArray(5) // Rotation Vector sensor or estimation
    private var lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var accl: FloatArray = FloatArray(3) // raw acceleration
    private var pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var hr: FloatArray = FloatArray(1) // Heart Rate
    private var hrRaw: FloatArray = FloatArray(16) // Samsung's Raw HR data
    private var gyro: FloatArray = FloatArray(3) // gyroscope
    private var magn: FloatArray = FloatArray(3) // magnetic
    private var grav: FloatArray = FloatArray(3) // gravity
    private var ppg: FloatArray = FloatArray(1) // gravity
    private var socket_start: Boolean = false
    private var start_stream: Boolean = false
    private var data: ArrayList<FloatArray> = ArrayList() // all recorded data
    // Events
    /**
     * Sensors are triggered in different frequencies and might have varying delays.
     * To fetch all data at the same time, this function is called in fixed timed intervals
     * by Timer in MainActivity.kt
     */

    fun startSocketAndStream(checked: Boolean){
        if (_currentState.value == STATE.ready){
            _currentState.value = STATE.stream
            }
        else{
            _currentState.value = STATE.ready
            }
        /* After changing state to stream now check if toggle is true (checked)
        and proceed with creating socket and streaming data
         */
        if (checked){
            println("Created tcp socket and streaming data")
            thread {
            streamData()
                }
            }
        else {
            println("Stopped streaming data!")
            }
        }

    fun streamData(){
        try {
            tcp_client =  Socket("192.168.1.148", 2020)
            while (_currentState.value == STATE.stream) {
//                        [rotVec  // rotation vector[5]  is a quaternion x,y,z,w, + confidence
//                        + lacc // [3] linear acceleration x,y,z
//                        + accl // [3] unfiltered acceleration x,y,z
//                        + pres  // [1] atmospheric pressure
//                        + gyro // [3] Angular speed around the x,y,z -axis
//                        + magn // [3] the ambient magnetic field in the x,y,z -axis
//                        + grav // [3] vector indicating the direction and magnitude of gravity x,y,z
//                        + hr // [1] heart rate in bpm
//                        + hrRaw] // [16] undocumented data from Samsung's Hr raw sensor
                var sensors = listOf<FloatArray>(rotVec, lacc, accl, pres, gyro, magn, grav, hr, hrRaw)
                var sensorDataList = listOf<String>()
                for (sensor in sensors){
                    for (d_ in sensor){
                        sensorDataList += d_.toString()
                    }
                }
//                println(rotVec[0..1])
                tcp_client.getOutputStream().write(sensorDataList.toString().toByteArray())
                TimeUnit.MILLISECONDS.sleep(10L)
                }
            tcp_client.shutdownOutput()
            tcp_client.close()
            return
        }
        catch (e: Exception){
            println("Got error $e")
            tcp_client.shutdownOutput()
            tcp_client.close()
            return
        }
    }

    fun recordSensorValues(secTime: Long) {

        // only record observations if the switch was turned on
        if (_currentState.value == STATE.recording) {
            // write to data
            data.add(
                floatArrayOf(secTime.toFloat())
                        + rotVec  // rotation vector[5]  is a quaternion x,y,z,w, + confidence
                        + lacc // [3] linear acceleration x,y,z
                        + accl // [3] unfiltered acceleration x,y,z
                        + pres  // [1] atmospheric pressure
                        + gyro // [3] Angular speed around the x,y,z -axis
                        + magn // [3] the ambient magnetic field in the x,y,z -axis
                        + grav // [3] vector indicating the direction and magnitude of gravity x,y,z
                        + hr // [1] heart rate in bpm
                        + hrRaw // [16] undocumented data from Samsung's Hr raw sensor
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

    fun onHrReadout(newReadout: FloatArray) {
        hr = newReadout
    }

    fun onHrRawReadout(newReadout: FloatArray) {
        hrRaw = newReadout
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
            "millisec," +
                    "qrot_x,qrot_y,qrot_z,qrot_w,qrot_conf," +
                    "lacc_x,lacc_y,lacc_z," +
                    "accl_x,accl_y,accl_z," +
                    "pres," +
                    "gyro_x,gyro_y,gyro_z," +
                    "magn_x,magn_y,magn_z," +
                    "grav_x,grav_y,grav_z," +
                    "hr," +
                    "hrRaw_0,hrRaw_1,hrRaw_2,hrRaw_3,hrRaw_4,hrRaw_5,hrRaw_6,hrRaw_7,hrRaw_8," +
                    "hrRaw_9,hrRaw_10,hrRaw_11,hrRaw_12,hrRaw_13,hrRaw_14,hrRaw_15\n"
        )
        // write row-by-row
        for (arr in data) {
            fOut.write("%d,".format(arr[0].toInt())) // milliseconds as integer

            for (entry in arr.slice(1 until arr.size - 1)) {
                fOut.write("%e,".format(entry))
            }
            fOut.write("%e\n".format(arr[arr.size - 1])) // new line at the end
        }
        fOut.flush()
        fOut.close()
    } catch (e: IOException) {
        e.printStackTrace()
        Log.v(TAG, "Log file creation failed.")
        return STATE.error
    }

    // Parse the file and path to uri
    Log.v(TAG, "Text file created at ${textFile.absolutePath}.")
    return STATE.ready
}