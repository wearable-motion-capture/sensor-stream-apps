package com.example.sensorrecord.presentation

import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
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

enum class CalibrationState {
    Start,
    Hold,
    Forward,
    Up,
    Down
}


enum class SensorHandlerState {
    Calibrating, // the app needs calibration
    Ready, // app waits for user to trigger the recording
    Recording, // recording sensor data into memory
    Processing, // processing sensor data from memory to CSV
    Error, // error state. Stop using the app,
    Streaming // streaming to IP and Port set in SensorViewModel
}

/**
 * This App follows the unidirectional data flow design pattern of the Jetpack Compose UI.
 * We keep the permanent state of the screen in this ViewModel.
 * It exposes the state as FlowData, which the view "observes" and reacts to.
 * The state is altered via callbacks (Events).
 */
class SensorHandler {
    
    companion object {
        private const val TAG = "SensorViewModel"  // for logging
    }

    // State
    // The interval in milliseconds between every sensor readout (1000/interval = Hz)
    private val _interval = 10L // a setting of 1 means basically as fast as possible

    // used by the recording function to zero out time stamps when writing to file
    private var _startRecordTimeStamp = LocalDateTime.now()

    // the default remote IP and Port to stream data
    var socketIP = "192.168.1.162"
    var socketPort = 50000

    // A change in MutableStateFlow values triggers a redraw of elements that use it
    private val _appState = MutableStateFlow(SensorHandlerState.Calibrating)
    val appState = _appState.asStateFlow()

    // calibration parameters
    private val _calibState = MutableStateFlow(CalibrationState.Start)
    val calibState = _calibState.asStateFlow()
    private var _initPressure = 0.0 // the initial pressure for relative pressure estimations
    private var _forwardNorthDegree =
        0.0 // magnetic north pole direction in relation to body orientation

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
        rotVec = newReadout // [x,y,z,w]
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
     * Triggered by the calibration button. It goes through all 4 calibration stages
     * to set required normalization parameters
     */
    fun calibrationTrigger(vibrator: Vibrator) {

        // verify that app is in a state that allows to start the calibration
        if (_calibState.value != CalibrationState.Start) {
            Log.v(TAG, "Calibration already in progress")
            return
        }

        fun forwardStep() {
            // the second step is the pressure when the arm is raised
            _calibState.value = CalibrationState.Forward
            thread {
                var lastBelow = LocalDateTime.now()
                var diff = 0L

                val northDegrees = mutableListOf(pres[0])
                while (diff < 2000) {
                    // the watch held horizontally if gravity in z direction is positive
                    if (grav[2] < 9.7) {
                        lastBelow = LocalDateTime.now()
                        northDegrees.clear()
                    }

                    northDegrees.add(getNorthDegree())
                    diff = Duration.between(lastBelow, LocalDateTime.now()).toMillis()
                }

                // last calibration step done
                _forwardNorthDegree =
                    northDegrees.average() + 90 // add 90 degress for forward orientation of arm and hip
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        500L, VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
                // set app state to ready to begin recording
                _appState.value = SensorHandlerState.Ready
            }
        }

        fun holdStep() {
            // begin with initial pressure in hold position
            _calibState.value = CalibrationState.Hold
            thread {
                val start = LocalDateTime.now()
                var diff = 0L

                val pressures = mutableListOf(pres[0])
                while (diff < 2000) {
                    pressures.add(pres[0])
                    diff = Duration.between(start, LocalDateTime.now()).toMillis()
                }
                // first calibration step done
                _initPressure = pressures.average()
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        500L, VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
                // continue with second step
                forwardStep()
            }
        }

        // this is the start of the calibration that calls all above functions iteratively
        holdStep()

// TODO: investigate how normalized pressure with UP and DOWN calibration performs
// var _minPressure = 0.0 // the minimal atmospheric pressure when the arm is raised up high
// var _maxPressure = 0.0 // the maximal atmospheric pressure when the arm is dangling down
//        fun downStep() {
//            // the second step is the pressure when the arm is raised
//            _calibState.value = CalibrationState.Down
//            thread {
//                var lastBelow = LocalDateTime.now()
//                var diff = 0L
//
//                val pressures = mutableListOf(pres[0])
//                while (diff < 2000) {
//                    // the watch is raised up high if gravity in x-direction is positive
//                    if (grav[0] > -9.6) {
//                        lastBelow = LocalDateTime.now()
//                        pressures.clear()
//                    }
//                    pressures.add(pres[0])
//                    diff = Duration.between(lastBelow, LocalDateTime.now()).toMillis()
//                }
//                // third calibration step done
//                _maxPressure = pressures.average()
//                vibrator.vibrate(
//                    VibrationEffect.createOneShot(
//                        500L, VibrationEffect.DEFAULT_AMPLITUDE
//                    )
//                )
//                // continue with last step
//                //forwardStep()
//            }
//        }
//
//        fun upStep() {
//            // the second step is the pressure when the arm is raised
//            _calibState.value = CalibrationState.Up
//            thread {
//                var lastBelow = LocalDateTime.now()
//                var diff = 0L
//
//                val pressures = mutableListOf(pres[0])
//                while (diff < 2000) {
//                    // the watch is raised up high if gravity in x-direction is positive
//                    if (grav[0] < 9.6) {
//                        lastBelow = LocalDateTime.now()
//                        pressures.clear()
//                    }
//                    pressures.add(pres[0])
//                    diff = Duration.between(lastBelow, LocalDateTime.now()).toMillis()
//                }
//                // second calibration step done
//                _minPressure = pressures.average()
//                vibrator.vibrate(
//                    VibrationEffect.createOneShot(
//                        500L, VibrationEffect.DEFAULT_AMPLITUDE
//                    )
//                )
//                // continue with third step
//                downStep()
//            }
//        }
    }

    /**
     * Triggered by the streaming ClipToggle onChecked event
     * opens a TCP socket and streams sensor data to set IP as long as the current stat is STATE.Streaming.
     */
    fun streamTrigger(checked: Boolean) {

        // verify that app is in a state that allows to start or stop streaming
        if (_appState.value != SensorHandlerState.Ready && _appState.value != SensorHandlerState.Streaming) {
            Log.v(TAG, "not ready to start or stop streaming")
            return
        }

        // if toggle is true (checked), proceed with creating a socket and begin to stream data
        if (checked) {
            _appState.value = SensorHandlerState.Streaming

            // run the streaming in a thread
            thread {
                // Create the tcpClient with set socket IP
                try {
                    val tcpClient = Socket(socketIP, socketPort)
                    val streamStartTimeStamp = LocalDateTime.now()

                    while (_appState.value == SensorHandlerState.Streaming) {
                        val diff =
                            Duration.between(streamStartTimeStamp, LocalDateTime.now()).toMillis()
                        // write to data
                        val sensorData = floatArrayOf(diff.toFloat()) +
                                rotVec +  // rotation vector[5]  is a quaternion x,y,z,w, + confidence
                                lacc + // [3] linear acceleration x,y,z
                                pres +  // [1] atmospheric pressure
                                grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                                floatArrayOf(_initPressure.toFloat()) + // initial atmospheric pressure collected during calibration
                                floatArrayOf(_forwardNorthDegree.toFloat()) // body orientation in relation to magnetic north pole collected during calibration


                        // write sensor data as string
                        var dataString = "#START,"
                        for (ety in sensorData) {
                            dataString += "%e,".format(ety)
                        }
                        dataString += "#END"

                        // transform into byte array
                        val dataPacketByte = dataString.toByteArray(Charset.defaultCharset())

                        // finally, send the byte stream
                        tcpClient.getOutputStream().write(dataPacketByte)
                        Thread.sleep(_interval)
                    }

                    tcpClient.close()
                } catch (e: Exception) {
                    Log.v(TAG, "Streaming error $e")
                    _appState.value = SensorHandlerState.Ready
                    Log.v(TAG, "stopped streaming")
                }
            }

        } else {
            _appState.value = SensorHandlerState.Ready
            Log.v(TAG, "stopped streaming")
        }
    }

    /**
     * changes with the recording ClipToggle
     * This function starts or stops the local recording of sensor data
     */
    fun recordTrigger(checked: Boolean) {
        // no actions allowed if not in ready or recording state
        if (_appState.value != SensorHandlerState.Ready && _appState.value != SensorHandlerState.Recording) {
            Log.v(TAG, "not ready to record or stop recording")
            return
        }
        if (checked) {
            //if turned on, record exact start time
            _appState.value = SensorHandlerState.Recording
            _startRecordTimeStamp = LocalDateTime.now()

            // Such that the while loop that doesn't block the co-routine
            thread {
                // A count for measurements taken. The delay function is not accurate because estimation time
                // must be added. Therefore, we keep track of passed time in a separate calculation
                var steps = 0

                while (_appState.value == SensorHandlerState.Recording) {
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
                                + floatArrayOf(_initPressure.toFloat()) // initial atmospheric pressure collected during calibration
                                + floatArrayOf(_forwardNorthDegree.toFloat()) // body orientation in relation to magnetic north pole collected during calibration
                    )
                    // delay by a given amount of milliseconds
                    Thread.sleep(_interval)
                    // increase step count to trigger LaunchedEffect again
                    steps += 1
                }
            }
        } else {
            // if turned off, safe data an clear
            _appState.value = SensorHandlerState.Processing
            // data processing in separate thread to not jack the UI
            thread {
                // next state determined by whether data processing was successful 
                _appState.value = saveToDatedCSV(_startRecordTimeStamp, data)
                data.clear()
            }
        }
    }

    /**
     * Parses data from SensorViewModel into a CSV with header and stores it into the public shared
     * Documents folder of the Android device that runs this app. The file name uses the input LocalDateTime
     * for a unique name.
     */
    private fun saveToDatedCSV(
        start: LocalDateTime,
        data: java.util.ArrayList<FloatArray>
    ): SensorHandlerState {

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
                        "hrRaw_9,hrRaw_10,hrRaw_11,hrRaw_12,hrRaw_13,hrRaw_14,hrRaw_15," +
                        "init_pres," +
                        "forward_north_degree\n"
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
            return SensorHandlerState.Error
        }

        // Parse the file and path to uri
        Log.v(TAG, "Text file created at ${textFile.absolutePath}.")
        return SensorHandlerState.Ready
    }

    /**
     * Estimates rotation around global y-axis (Up) from watch orientation.
     * This corresponds to the azimuth in polar coordinates. It the angle from the z-axis (forward)
     * in between +pi and -pi.
     */
    private fun getNorthDegree(): Float {
        // smartwatch rotation to [-w,x,z,y]
        val r = floatArrayOf(-rotVec[3], rotVec[0], rotVec[2], rotVec[1])
        val p = floatArrayOf(0f, 0f, 0f, 1f) // forward vector with [0,x,y,z]

        // this is the result of H(R,P)
        val hrp = floatArrayOf(
            r[0] * p[0] - r[1] * p[1] - r[2] * p[2] - r[3] * p[3],
            r[0] * p[1] + r[1] * p[0] + r[2] * p[3] - r[3] * p[2],
            r[0] * p[2] - r[1] * p[3] + r[2] * p[0] + r[3] * p[1],
            r[0] * p[3] + r[1] * p[2] - r[2] * p[1] + r[3] * p[0]
        )

        val r_p = floatArrayOf(r[0], -r[1], -r[2], -r[3]) // this ir R'
        // the final H(H(R,P),R')
        val p_p = floatArrayOf(
            hrp[0] * r_p[0] - hrp[1] * r_p[1] - hrp[2] * r_p[2] - hrp[3] * r_p[3],
            hrp[0] * r_p[1] + hrp[1] * r_p[0] + hrp[2] * r_p[3] - hrp[3] * r_p[2],
            hrp[0] * r_p[2] - hrp[1] * r_p[3] + hrp[2] * r_p[0] + hrp[3] * r_p[1],
            hrp[0] * r_p[3] + hrp[1] * r_p[2] - hrp[2] * r_p[1] + hrp[3] * r_p[0]
        )
        // get angle with atan2
        val yRot = kotlin.math.atan2(p_p[1], p_p[3])

        return yRot * 57.29578f
    }
}
