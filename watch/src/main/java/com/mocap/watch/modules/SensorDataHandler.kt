package com.mocap.watch.modules

import android.os.Environment
import android.util.Log
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

class SensorDataHandler(globalState: GlobalState, calibrator: SensorCalibrator) {
    /** setup-specific parameters */
    companion object {
        private const val TAG = "SensorDataHandler"  // for logging
        private const val STREAM_INTERVAL = 10L
        private const val PORT = 50000
    }

    private val _gs = globalState
    private val _calib = calibrator

    // used by the recording function to zero out time stamps when writing to file
    private var _startRecordTimeStamp = LocalDateTime.now()

    // Internal sensor reads that get updated as fast as possible
    private var data: ArrayList<FloatArray> = ArrayList() // all recorded data

    /**
     * Triggered by the streaming ClipToggle onChecked event
     * opens a TCP socket and streams sensor data to set IP as long as the current stat is STATE.Streaming.
     */
    fun triggerImuStreamUdp(checked: Boolean) {

        // verify that app is in a state that allows to start or stop streaming
        if (_gs.getSensorState() != SensorDataHandlerState.Idle &&
            _gs.getSensorState() != SensorDataHandlerState.Streaming
        ) {
            Log.v(TAG, "not ready to start or stop streaming")
            return
        }

        // if toggle is true (checked), proceed with creating a socket and begin to stream data
        if (checked) {
            _gs.setSensorState(SensorDataHandlerState.Streaming)

            // run the streaming in a thread
            thread {
                // Create the tcpClient with set socket IP
                try {
                    val udpSocket = DatagramSocket(PORT)
                    udpSocket.broadcast = true
                    val socketInetAddress = InetAddress.getByName(_gs.getIP())
                    val streamStartTimeStamp = LocalDateTime.now()

                    while (_gs.getSensorState() == SensorDataHandlerState.Streaming) {
                        val diff =
                            Duration.between(streamStartTimeStamp, LocalDateTime.now()).toMillis()
                        // write to data
                        val sensorData = floatArrayOf(diff.toFloat()) +
                                _gs.getSensorReadingStream() +
                                floatArrayOf(
                                    _calib.initPres.value.toFloat(), // initial atmospheric pressure collected during calibration
                                    _calib.northDeg.value.toFloat() // body orientation in relation to magnetic north pole collected during calibration
                                )

//                        // write sensor data as string
//                        var dataString = "#START,"
//                        for (ety in sensorData) {
//                            dataString += "%e,".format(ety)
//                        }
//                        dataString += "#END"

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
                    _gs.setSensorState(SensorDataHandlerState.Idle)
                    Log.v(TAG, "stopped streaming")
                }
            }

        } else {
            _gs.setSensorState(SensorDataHandlerState.Idle)
            Log.v(TAG, "stopped streaming")
        }
    }

    /**
     * changes with the recording ClipToggle
     * This function starts or stops the local recording of sensor data
     */
    fun recordTrigger(checked: Boolean) {
        // no actions allowed if not in ready or recording state
        if (_gs.getSensorState() != SensorDataHandlerState.Idle &&
            _gs.getSensorState() != SensorDataHandlerState.Recording
        ) {
            Log.v(TAG, "not ready to record or stop recording")
            return
        }
        if (checked) {
            //if turned on, record exact start time
            _gs.setSensorState(SensorDataHandlerState.Recording)
            _startRecordTimeStamp = LocalDateTime.now()

            // Such that the while loop that doesn't block the co-routine
            thread {
                // A count for measurements taken. The delay function is not accurate because estimation time
                // must be added. Therefore, we keep track of passed time in a separate calculation
                var steps = 0

                while (_gs.getSensorState() == SensorDataHandlerState.Recording) {
                    // estimate time difference to given start point as our time stamp
                    val diff =
                        Duration.between(_startRecordTimeStamp, LocalDateTime.now()).toMillis()
                    // write to data
                    data.add(
                        floatArrayOf(diff.toFloat()) +
                                _gs.getSensorReadingRecord() +
                                floatArrayOf(_calib.initPres.value.toFloat()) + // initial atmospheric pressure collected during calibration
                                floatArrayOf(_calib.northDeg.value.toFloat()) // body orientation in relation to magnetic north pole collected during calibration
                    )
                    // delay by a given amount of milliseconds
                    Thread.sleep(STREAM_INTERVAL)
                    // increase step count to trigger LaunchedEffect again
                    steps += 1
                }
            }
        } else {
            // if turned off, safe data an clear
            _gs.setSensorState(SensorDataHandlerState.Processing)
            // data processing in separate thread to not jack the UI
            thread {
                // next state determined by whether data processing was successful
                _gs.setSensorState(saveToDatedCSV(_startRecordTimeStamp, data))
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
}
