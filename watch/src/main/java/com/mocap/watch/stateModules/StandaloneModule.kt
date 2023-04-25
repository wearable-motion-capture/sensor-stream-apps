package com.mocap.watch.stateModules

import android.os.Environment
import android.util.Log
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

enum class SensorDataHandlerState {
    Idle, // app waits for user to trigger the recording
    Recording, // recording sensor data into memory
    Processing, // processing sensor data from memory to CSV
    Error, // error state. Stop using the app,
    Streaming // streaming to IP and Port set in StateModule
}

class StandaloneModule {
    /** setup-specific parameters */
    companion object {
        private const val TAG = "StandaloneModule"  // for logging
        private const val STREAM_INTERVAL = 10L
        private const val PORT = 50000
    }

    private val _sensorStrState = MutableStateFlow(SensorDataHandlerState.Idle)
    val sensorStrState = _sensorStrState.asStateFlow()

    private val _ip = DataSingleton.IP.value

    // used by the recording function to zero out time stamps when writing to file
    private var _startRecordTimeStamp = LocalDateTime.now()

    // Internal sensor reads that get updated as fast as possible
    private var data: ArrayList<FloatArray> = ArrayList() // all recorded data

    // callbacks will write to these variables
    private var _rotVec: FloatArray = FloatArray(5) // Rotation Vector sensor or estimation
    private var _lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var _accl: FloatArray = FloatArray(3) // raw acceleration
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var _hr: FloatArray = FloatArray(1) // Heart Rate
    private var _hrRaw: FloatArray = FloatArray(16) // Samsung's Raw HR data
    private var _gyro: FloatArray = FloatArray(3) // gyroscope
    private var _magn: FloatArray = FloatArray(3) // magnetic


    fun reset(){
        _sensorStrState.value = SensorDataHandlerState.Idle
    }

    /**
     * Triggered by the streaming ClipToggle UI element.
     * Opens a TCP socket and streams sensor data to set IP as long as
     * the current state is STATE.Streaming.
     */
    fun triggerImuStreamUdp(checked: Boolean) {

        // verify that app is in a state that allows to start or stop streaming
        if (sensorStrState.value != SensorDataHandlerState.Idle &&
            sensorStrState.value != SensorDataHandlerState.Streaming
        ) {
            Log.v(TAG, "not ready to start or stop streaming")
            return
        }

        // if toggle is true (checked), proceed with creating a socket and begin to stream data
        if (checked) {
            _sensorStrState.value = SensorDataHandlerState.Streaming
            val press = DataSingleton.CALIB_PRESS.value.toFloat()
            val north = DataSingleton.CALIB_NORTH.value.toFloat()

            // run the streaming in a thread
            thread {
                // Create the tcpClient with set socket IP
                try {
                    val udpSocket = DatagramSocket(PORT)
                    udpSocket.broadcast = true
                    val socketInetAddress = InetAddress.getByName(_ip)
                    val streamStartTimeStamp = LocalDateTime.now()

                    while (sensorStrState.value == SensorDataHandlerState.Streaming) {
                        val diff =
                            Duration.between(streamStartTimeStamp, LocalDateTime.now()).toMillis()
                        // write to data
                        val sensorData = floatArrayOf(diff.toFloat()) +
                                _rotVec + // rotation vector[5]  is a quaternion [x,y,z,w,confidence]
                                _lacc + // [3] linear acceleration x,y,z
                                _pres + // [1] atmospheric pressure
                                _grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                                _gyro + // [3] gyro data for time series prediction
                                _hrRaw + // [16] undocumented data from Samsung's Hr raw sensor
                                floatArrayOf(
                                    press, // initial atmospheric pressure collected during calibration
                                    north // body orientation in relation to magnetic north pole collected during calibration
                                )

                        val buffer = ByteBuffer.allocate(4 * sensorData.size)
                        for (v in sensorData) {
                            buffer.putFloat(v)
                        }
                        val dp = DatagramPacket(
                            buffer.array(),
                            buffer.capacity(),
                            socketInetAddress,
                            PORT
                        )
                        // finally, send the byte stream
                        udpSocket.send(dp)
                        Thread.sleep(STREAM_INTERVAL)
                    }
                    udpSocket.close()
                } catch (e: Exception) {
                    Log.v(TAG, "Streaming error $e")
                    _sensorStrState.value = SensorDataHandlerState.Idle
                    Log.v(TAG, "stopped streaming")
                }
            }

        } else {
            _sensorStrState.value = SensorDataHandlerState.Idle
            Log.v(TAG, "stopped streaming")
        }
    }

    /**
     * changes with the recording ClipToggle
     * This function starts or stops the local recording of sensor data
     */
    fun recordTrigger(checked: Boolean) {
        // no actions allowed if not in ready or recording state
        if (sensorStrState.value != SensorDataHandlerState.Idle &&
            sensorStrState.value != SensorDataHandlerState.Recording
        ) {
            Log.v(TAG, "not ready to record or stop recording")
            return
        }
        if (checked) {
            //if turned on, record exact start time
            _sensorStrState.value = SensorDataHandlerState.Recording
            _startRecordTimeStamp = LocalDateTime.now()
            val press = DataSingleton.CALIB_PRESS.value.toFloat()
            val north = DataSingleton.CALIB_NORTH.value.toFloat()

            // Such that the while loop that doesn't block the co-routine
            thread {
                // A count for measurements taken. The delay function is not accurate because estimation time
                // must be added. Therefore, we keep track of passed time in a separate calculation
                var steps = 0

                while (sensorStrState.value == SensorDataHandlerState.Recording) {
                    // estimate time difference to given start point as our time stamp
                    val diff =
                        Duration.between(_startRecordTimeStamp, LocalDateTime.now()).toMillis()
                    // write to data
                    data.add(
                        floatArrayOf(diff.toFloat()) +
                                _rotVec + // rotation vector[5]  is a quaternion [x,y,z,w,confidence]
                                _lacc + // [3] linear acceleration x,y,z
                                _pres + // [1] atmospheric pressure
                                _grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                                _gyro + // [3] gyro data for time series prediction
                                _hrRaw + // [16] undocumented data from Samsung's Hr raw sensor
                                floatArrayOf(
                                    press, // initial atmospheric pressure collected during calibration
                                    north // body orientation in relation to magnetic north pole collected during calibration
                                )
                    )
                    // delay by a given amount of milliseconds
                    Thread.sleep(STREAM_INTERVAL)
                    // increase step count to trigger LaunchedEffect again
                    steps += 1
                }
            }
        } else {
            // if turned off, safe data an clear
            _sensorStrState.value = SensorDataHandlerState.Processing
            // data processing in separate thread to not jack the UI
            thread {
                // next state determined by whether data processing was successful
                _sensorStrState.value = saveToDatedCSV(_startRecordTimeStamp, data)
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
    ): SensorDataHandlerState {

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
            return SensorDataHandlerState.Error
        }

        // Parse the file and path to uri
        Log.v(TAG, "Text file created at ${textFile.absolutePath}.")
        return SensorDataHandlerState.Idle
    }

    // Events
    /** sensor callbacks */
    // Individual sensor reads are triggered by their onValueChanged events
    fun onLaccReadout(newReadout: FloatArray) {
        _lacc = newReadout
    }

    fun onRotVecReadout(newReadout: FloatArray) {
        _rotVec = newReadout // [x,y,z,w]
    }

    fun onAcclReadout(newReadout: FloatArray) {
        _accl = newReadout
    }

    fun onGyroReadout(newReadout: FloatArray) {
        _gyro = newReadout
    }

    fun onMagnReadout(newReadout: FloatArray) {
        _magn = newReadout
    }

    fun onHrReadout(newReadout: FloatArray) {
        _hr = newReadout
    }

    fun onHrRawReadout(newReadout: FloatArray) {
        _hrRaw = newReadout
    }

    fun onPressureReadout(newReadout: FloatArray) {
        _pres = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }
}
