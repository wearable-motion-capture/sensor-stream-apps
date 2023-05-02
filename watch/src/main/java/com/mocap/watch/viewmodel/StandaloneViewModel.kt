package com.mocap.watch.viewmodel

import android.Manifest
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import com.mocap.watch.DataSingleton
import com.mocap.watch.SensorStreamState
import com.mocap.watch.SoundStreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer


class StandaloneViewModel(application: Application) :
    AndroidViewModel(application) {

    /** setup-specific parameters */
    companion object {
        private const val TAG = "StandaloneModule"  // for logging
        private const val IMU_STREAM_INTERVAL = 10L
        private const val RECORDING_RATE = 16000 // can go up to 44K, if needed
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BUFFER_SIZE = 1600
    }

    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private val _sensorStrState = MutableStateFlow(SensorStreamState.Idle)
    val sensorStrState = _sensorStrState.asStateFlow()

    private val _soundStrState = MutableStateFlow(SoundStreamState.Idle)
    val soundStrState = _soundStrState.asStateFlow()

    // callbacks will write to these variables
    private var _rotVec: FloatArray = FloatArray(5) // Rotation Vector sensor or estimation
    private var _lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var _accl: FloatArray = FloatArray(3) // raw acceleration
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var _hrRaw: FloatArray = FloatArray(16) // Samsung's Raw HR data
    private var _gyro: FloatArray = FloatArray(3) // gyroscope
    private var _magn: FloatArray = FloatArray(3) // magnetic

    override fun onCleared() {
        _sensorStrState.value = SensorStreamState.Idle
        _soundStrState.value = SoundStreamState.Idle
        _scope.cancel()
        Log.d(TAG, "Cleared")
    }


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun triggerMicStream(checked: Boolean) {
        if (!checked) {
            _soundStrState.value = SoundStreamState.Idle
        } else {
            if (_soundStrState.value == SoundStreamState.Streaming) {
                throw Exception("The SoundStreamState.Streaming should not be active at start")
            }
            // Create the tcpClient with set socket IP
            _scope.launch { soundStreamUdp() }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun soundStreamUdp() {
        val ip = DataSingleton.IP.value
        val port = DataSingleton.UDP_AUDIO_PORT

        // run the streaming in a thread
        withContext(Dispatchers.IO) {
            // Create an AudioRecord object for the streaming
            val audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RECORDING_RATE)
                        .setChannelMask(CHANNEL_IN)
                        .setEncoding(FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(AUDIO_BUFFER_SIZE)
                .build()

            // begin streaming the microphone
            audioRecord.startRecording()
            Log.w(TAG, "Started Recording")

            // open a socket
            val udpSocket = DatagramSocket(port)
            udpSocket.broadcast = true
            val socketInetAddress = InetAddress.getByName(ip)
            Log.v(TAG, "Opened UDP socket to $ip:$port")

            udpSocket.use {
                // read from audioRecord stream and send through to UDP output stream
                _soundStrState.value = SoundStreamState.Streaming
                while (soundStrState.value == SoundStreamState.Streaming) {
                    val buffer = ByteBuffer.allocate(AUDIO_BUFFER_SIZE)
                    audioRecord.read(buffer.array(), 0, buffer.capacity())
                    val dp = DatagramPacket(
                        buffer.array(),
                        buffer.capacity(),
                        socketInetAddress,
                        port
                    )
                    udpSocket.send(dp)
                }
            }
            // make sure to release the audio recorder
            audioRecord.release()
            Log.v(TAG, "Release")
        }
    }


    /**
     * Triggered by the streaming ClipToggle UI element.
     * Opens a TCP socket and streams sensor data to set IP as long as
     * the current state is STATE.Streaming.
     */
    fun triggerImuStreamUdp(checked: Boolean) {
        if (!checked) {
            _sensorStrState.value = SensorStreamState.Idle
        } else {
            if (_sensorStrState.value == SensorStreamState.Streaming) {
                throw Exception("The StreamState.Streaming should not be active at start")
            }
            // Create the tcpClient with set socket IP
            _scope.launch { imuStreamUdp() }
        }
    }


    private suspend fun imuStreamUdp() {
        val press = DataSingleton.CALIB_PRESS.value
        val north = DataSingleton.CALIB_NORTH.value.toFloat()
        val ip = DataSingleton.IP.value
        val port = DataSingleton.UDP_IMU_PORT
        val msgSize = DataSingleton.WATCH_MESSAGE_SIZE


        // run the streaming in a thread
        withContext(Dispatchers.IO) {
            // open a socket
            val udpSocket = DatagramSocket(port)
            udpSocket.broadcast = true
            val socketInetAddress = InetAddress.getByName(ip)
            Log.v(TAG, "Opened UDP socket to $ip:$port")

            udpSocket.use {
                _sensorStrState.value = SensorStreamState.Streaming
                while (sensorStrState.value == SensorStreamState.Streaming) {
                    // write to data
                    val sensorData = _rotVec + // rotation vector[4]  is a quaternion [w,x,y,z]
                            _lacc + // [3] linear acceleration x,y,z
                            _pres + // [1] atmospheric pressure
                            _grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                            _gyro + // [3] gyro data for time series prediction
                            _hrRaw + // [16] undocumented data from Samsung's Hr raw sensor
                            floatArrayOf(
                                press, // initial atmospheric pressure collected during calibration
                                north // body orientation in relation to magnetic north pole collected during calibration
                            )
                    val buffer = ByteBuffer.allocate(4 * msgSize)
                    for (v in sensorData) {
                        buffer.putFloat(v)
                    }
                    val dp = DatagramPacket(
                        buffer.array(),
                        buffer.capacity(),
                        socketInetAddress,
                        port
                    )
                    // finally, send the byte stream
                    udpSocket.send(dp)
                    delay(IMU_STREAM_INTERVAL)
                }
            }
        }
    }


    /** sensor callbacks */
    // Individual sensor reads are triggered by their onValueChanged events
    fun onLaccReadout(newReadout: FloatArray) {
        _lacc = newReadout
    }

    fun onRotVecReadout(newReadout: FloatArray) {
        // newReadout is [x,y,z,w, confidence]
        // our preferred order system is [w,x,y,z]
        _rotVec = floatArrayOf(
            newReadout[3],
            newReadout[0],
            newReadout[1],
            newReadout[2]
        )
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

    fun onHrRawReadout(newReadout: FloatArray) {
        _hrRaw = newReadout
    }

    fun onPressureReadout(newReadout: FloatArray) {
        _pres = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }

//    /**
//     * changes with the recording ClipToggle
//     * This function starts or stops the local recording of sensor data
//     */
//    fun recordTrigger(checked: Boolean) {
//        // no actions allowed if not in ready or recording state
//        if (sensorStrState.value != SensorStreamState.Idle &&
//            sensorStrState.value != SensorStreamState.Recording
//        ) {
//            Log.v(TAG, "not ready to record or stop recording")
//            return
//        }
//        if (checked) {
//            //if turned on, record exact start time
//            _sensorStrState.value = SensorStreamState.Recording
//            _startRecordTimeStamp = LocalDateTime.now()
//            val press = DataSingleton.CALIB_PRESS.value.toFloat()
//            val north = DataSingleton.CALIB_NORTH.value.toFloat()
//
//            // Such that the while loop that doesn't block the co-routine
//            thread {
//                // A count for measurements taken. The delay function is not accurate because estimation time
//                // must be added. Therefore, we keep track of passed time in a separate calculation
//                var steps = 0
//
//                while (sensorStrState.value == SensorStreamState.Recording) {
//                    // estimate time difference to given start point as our time stamp
//                    val diff =
//                        Duration.between(_startRecordTimeStamp, LocalDateTime.now()).toMillis()
//                    // write to data
//                    data.add(
//                        floatArrayOf(diff.toFloat()) +
//                                _rotVec + // rotation vector[5]  is a quaternion [x,y,z,w,confidence]
//                                _lacc + // [3] linear acceleration x,y,z
//                                _pres + // [1] atmospheric pressure
//                                _grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
//                                _gyro + // [3] gyro data for time series prediction
//                                _hrRaw + // [16] undocumented data from Samsung's Hr raw sensor
//                                floatArrayOf(
//                                    press, // initial atmospheric pressure collected during calibration
//                                    north // body orientation in relation to magnetic north pole collected during calibration
//                                )
//                    )
//                    // delay by a given amount of milliseconds
//                    Thread.sleep(STREAM_INTERVAL)
//                    // increase step count to trigger LaunchedEffect again
//                    steps += 1
//                }
//            }
//        } else {
//            // if turned off, safe data an clear
//            _sensorStrState.value = SensorStreamState.Processing
//            // data processing in separate thread to not jack the UI
//            thread {
//                // next state determined by whether data processing was successful
//                _sensorStrState.value = saveToDatedCSV(_startRecordTimeStamp, data)
//                data.clear()
//            }
//        }
//    }
//
//    /**
//     * Parses data from SensorViewModel into a CSV with header and stores it into the public shared
//     * Documents folder of the Android device that runs this app. The file name uses the input LocalDateTime
//     * for a unique name.
//     */
//    private fun saveToDatedCSV(
//        start: LocalDateTime,
//        data: java.util.ArrayList<FloatArray>
//    ): SensorStreamState {
//
//        // create unique filename from current date and time
//        val currentDate = (DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")).format(start)
//        val fileName = "sensorrecord_${currentDate}.csv"
//
//        // permission rules only allow to write into the public shared directory
//        // /storage/emulated/0/Documents/_2022-09-273_05-08-49.csv
//        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
//        val textFile = File(path, fileName)
//
//        // try to write data into a file at above location
//        try {
//            val fOut = FileWriter(textFile)
//            // write header
//            fOut.write(
//                "millisec," +
//                        "qrot_x,qrot_y,qrot_z,qrot_w,qrot_conf," +
//                        "lacc_x,lacc_y,lacc_z," +
//                        "accl_x,accl_y,accl_z," +
//                        "pres," +
//                        "gyro_x,gyro_y,gyro_z," +
//                        "magn_x,magn_y,magn_z," +
//                        "grav_x,grav_y,grav_z," +
//                        "hr," +
//                        "hrRaw_0,hrRaw_1,hrRaw_2,hrRaw_3,hrRaw_4,hrRaw_5,hrRaw_6,hrRaw_7,hrRaw_8," +
//                        "hrRaw_9,hrRaw_10,hrRaw_11,hrRaw_12,hrRaw_13,hrRaw_14,hrRaw_15," +
//                        "init_pres," +
//                        "forward_north_degree\n"
//            )
//            // write row-by-row
//            for (arr in data) {
//                fOut.write("%d,".format(arr[0].toInt())) // milliseconds as integer
//
//                for (entry in arr.slice(1 until arr.size - 1)) {
//                    fOut.write("%e,".format(entry))
//                }
//                fOut.write("%e\n".format(arr[arr.size - 1])) // new line at the end
//            }
//            fOut.flush()
//            fOut.close()
//        } catch (e: IOException) {
//            e.printStackTrace()
//            Log.v(TAG, "Log file creation failed.")
//            return SensorStreamState.Error
//        }
//
//        // Parse the file and path to uri
//        Log.v(TAG, "Text file created at ${textFile.absolutePath}.")
//        return SensorStreamState.Idle
//    }

}
