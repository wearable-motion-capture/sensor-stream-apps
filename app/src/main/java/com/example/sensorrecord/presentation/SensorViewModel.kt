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
import java.nio.charset.Charset
import java.time.Duration

// for logging
private const val TAG = "SensorViewModel"

enum class STATE {
    Ready, // app waits for user to trigger the recording
    Recording, // recording sensor data into memory
    Processing, // processing sensor data from memory into CSV. When finished, back to WAITING
    Error, // error state. Stop using the app,
    Streaming
}

/**
 * This App follows the unidirectional data flow design pattern of the Jetpack Compose UI.
 * We keep the permanent state of the screen in this ViewModel.
 * It exposes the state as FlowData, which the view "observes" and reacts to.
 * The state is altered via callbacks (Events).
 */
class SensorViewModel : ViewModel() {
    // State
    // The interval in milliseconds between every sensor readout (1000/interval = Hz)
    private val _interval = 10L // a setting of 1 means basically as fast as possible

    // used by the recording function to zero out time stamps when writing to file
    private var _startRecordTimeStamp = LocalDateTime.now()

    // the default IP to stream data to
    var socketIP = "192.168.1.162"

    // A change in MutableStateFlow values triggers a redraw of elements that use it
    private val _currentState = MutableStateFlow(STATE.Ready)
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
    private var data: ArrayList<FloatArray> = ArrayList() // all recorded data

    // Events
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

    /**
     * Triggered by the streaming ClipToggle onChecked event
     * opens a TCP socket and streams sensor data to set IP as long as the current stat is STATE.Streaming.
     */
    fun streamTrigger(checked: Boolean) {

        // verify that app is in a state that allows to start or stop streaming
        if (_currentState.value != STATE.Ready && _currentState.value != STATE.Streaming) {
            Log.v(TAG, "not ready to start or stop streaming")
            return
        }

        // if toggle is true (checked), proceed with creating a socket and begin to stream data
        if (checked) {
            _currentState.value = STATE.Streaming

            // run the streaming in a thread
            thread {
                // Create the tcpClient with set socket IP
                try {
                    val tcpClient = Socket(socketIP, 2020)

                    while (_currentState.value == STATE.Streaming) {
                        val sensorData =
                            listOf(rotVec, lacc, accl, pres, gyro, magn, grav, hr, hrRaw)
                        var dataPacket = "START," + sensorData.joinToString() + ",END,"
                        if (dataPacket.length < 512) {
                            val remainingBytes = 512 - dataPacket.length
                            var tmp = 0
                            while (tmp < remainingBytes) {
                                dataPacket += 'N'
                                tmp += 1
                            }
                        }

                        val dataPacketByte = dataPacket.toByteArray(Charset.defaultCharset())
                        Log.v(TAG, "data pacaket len; ${dataPacketByte.size}")
                        tcpClient.getOutputStream().write(dataPacketByte)
                        Thread.sleep(_interval)
                    }

                    tcpClient.close()
                } catch (e: Exception) {
                    Log.v(TAG, "Streaming error ${e}")
                    _currentState.value = STATE.Error
                }
            }

        } else {
            _currentState.value = STATE.Ready
            Log.v(TAG, "stopped streaming")
        }
    }

    /**
     * changes with the recording ClipToggle
     * This function starts or stops the local recording of sensor data
     */
    fun recordTrigger(checked: Boolean) {
        // no actions allowed if not in ready or recording state
        if (_currentState.value != STATE.Ready && _currentState.value != STATE.Recording) {
            Log.v(TAG, "not ready to record or stop recording")
            return
        }
        if (checked) {
            //if turned on, record exact start time
            _currentState.value = STATE.Recording
            _startRecordTimeStamp = LocalDateTime.now()

            // Such that the while loop that doesn't block the co-routine
            thread {
                // A count for measurements taken. The delay function is not accurate because estimation time
                // must be added. Therefore, we keep track of passed time in a separate calculation
                var steps = 0

                while (_currentState.value == STATE.Recording) {
                    // estimate time difference to given start point as our time stamp
                    val diff =
                        Duration.between(_startRecordTimeStamp, LocalDateTime.now()).toMillis()
                    // write to data
                    data.add(
                        floatArrayOf(diff.toFloat())
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
                    // delay by a given amount of milliseconds
                    Thread.sleep(_interval)
                    // increase step count to trigger LaunchedEffect again
                    steps += 1
                }
            }
        } else {
            // if turned off, safe data an clear
            _currentState.value = STATE.Processing
            // data processing in separate thread to not jack the UI
            thread {
                // next state determined by whether data processing was successful 
                _currentState.value = saveToDatedCSV(_startRecordTimeStamp, data)
                data.clear()
            }
        }
    }

    /**
     * Parses data from SensorViewModel into a CSV with header and stores it into the public shared
     * Documents folder of the Android device that runs this app. The file name uses the input LocalDateTime
     * for a unique name.
     */
    private fun saveToDatedCSV(start: LocalDateTime, data: java.util.ArrayList<FloatArray>): STATE {

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
            return STATE.Error
        }

        // Parse the file and path to uri
        Log.v(TAG, "Text file created at ${textFile.absolutePath}.")
        return STATE.Ready
    }
}
