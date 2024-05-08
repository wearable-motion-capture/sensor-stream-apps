package com.mocap.phone.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import com.mocap.phone.modules.PhoneChannelCallback
import com.mocap.phone.modules.SensorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Float.min
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime
import com.mocap.phone.utility.quatAverage
import java.io.File
import java.io.FileWriter
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.round

class ImuService : Service() {

    companion object {
        private const val TAG = "Channel IMU Service"  // for logging
        private const val NS2S = 1.0f / 1000000000.0f //Nano second to second
        private const val MS2S = 0.001f
        private const val MSGBREAK = 4L
    }

    /**
     * This service is dormant until Channels from the watch are opened. This callback triggers
     * when Channel states change.
     */
    private val _channelCallback = PhoneChannelCallback(
        openCallback = { onChannelOpen(it) },
        closeCallback = { onChannelClose(it) }
    )

    private lateinit var _sensorManager: SensorManager
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private var _lastBroadcast = LocalDateTime.now()

    private var _lastMsg: LocalDateTime? = null
    private var _calibrationDatList = mutableListOf<FloatArray>()


    // service state indicators
    private var _imuStreamState = false
    private var _swInCount: Int = 0
    private var _swOutCount: Int = 0
    private var _swQueue = ConcurrentLinkedQueue<ByteArray>()

    // Sensor values
    // callbacks will write to these variables
    private var _dpLvel: FloatArray = floatArrayOf(0f, 0f, 0f) // integrated linear acc
    private var _tsLacc: Long = 0 // time step of acc update in nano seconds
    private var _tsDLacc: Float = 0f // time since last update

    // gyroscope
    private var _dGyro: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var _tsGyro: Long = 0
    private var _tsDGyro: Float = 0f

    // other modalities
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)

    // rot vector as [w,x,y,z, conf] quat + confidence
    private var _rotvec: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 0f)

    // store listeners in this list to register and unregister them automatically
    private val _listeners = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { onLaccReadout(it) },
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { onGyroReadout(it) }
    )

    override fun onCreate() {
        // assign our sensor manager variable now that we are sure it has been initialized
        _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        Log.v(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _channelClient.registerChannelCallback(_channelCallback)
        _scope.launch {
            while (true) {
                broadcastUiUpdate()
                delay(2000L)
            }
        }
        Log.v(TAG, "Service started")
        return START_NOT_STICKY
    }

    /** Broadcasts service status values to view model to update the UI */
    private fun broadcastUiUpdate() {
        // duration estimate in seconds for Hz
        val now = LocalDateTime.now()
        val diff = Duration.between(_lastBroadcast, now)
        val ds = diff.toMillis() * MS2S
        _lastBroadcast = LocalDateTime.now()


        val intent = Intent(DataSingleton.BROADCAST_UPDATE)
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.IMU_PATH
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_STATE, _imuStreamState
        )
        // if the last broadcast was too recent
        if (ds <= 0F) {
            intent.putExtra(DataSingleton.BROADCAST_SERVICE_HZ_IN, 0F)
            intent.putExtra(DataSingleton.BROADCAST_SERVICE_HZ_OUT, 0F)
        } else {
            intent.putExtra(
                DataSingleton.BROADCAST_SERVICE_HZ_IN, round(_swInCount.toFloat() / ds)
            )
            intent.putExtra(
                DataSingleton.BROADCAST_SERVICE_HZ_OUT, round(_swOutCount.toFloat() / ds)
            )
        }
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_QUEUE, _swQueue.count()
        )
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        _swInCount = 0
        _swOutCount = 0
    }

    /** reads watch IMU messages from _imuPpgQueue and writes them to a file */
    private suspend fun recordImuMessages(c: ChannelClient.Channel) {
        try {
            withContext(Dispatchers.IO) {
                var calibrationDat = DataSingleton.watchQuat.value +
                        DataSingleton.phoneQuat.value +
                        floatArrayOf(DataSingleton.watchPres.value)
                // register all sensor listeners
                for (l in _listeners) {
                    _sensorManager.registerListener(
                        l,
                        _sensorManager.getDefaultSensor(l.code),
                        SensorManager.SENSOR_DELAY_FASTEST
                    )
                }

                // create unique filename from current date and time
                val currentDate =
                    (DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")).format(LocalDateTime.now())
                val fileName =
                    "rec_phone_pocket_" +
                            "${DataSingleton.recordActivityLabel.value}_" +
                            "${DataSingleton.addFileId.value}_" +
                            "${currentDate}.csv"

                // permission rules only allow to write into the public shared directory
                // /storage/emulated/0/Documents/_2022-09-273_05-08-49.csv
                val path = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS
                )
                val textFile = File(path, fileName)

                // try to write data into a file at above location
                val fOut = FileWriter(textFile)
                // write header
                fOut.write(
                    "sw_dt," +
                            "sw_h,sw_m,sw_s,sw_ns," +
                            "sw_rotvec_w,sw_rotvec_x,sw_rotvec_y,sw_rotvec_z,sw_rotvec_conf," +
                            "sw_gyro_x,sw_gyro_y,sw_gyro_z," +
                            "sw_lvel_x,sw_lvel_y,sw_lvel_z," +
                            "sw_lacc_x,sw_lacc_y,sw_lacc_z," +
                            "sw_pres," +
                            "sw_grav_x,sw_grav_y,sw_grav_z," +
                            "ph_dt," +
                            "ph_h,ph_m,ph_s,ph_ns," +
                            "ph_rotvec_w,ph_rotvec_x,ph_rotvec_y,ph_rotvec_z,ph_rotvec_conf," +
                            "ph_gyro_x,ph_gyro_y,ph_gyro_z," +
                            "ph_lvel_x,ph_lvel_y,ph_lvel_z," +
                            "ph_lacc_x,ph_lacc_y,ph_lacc_z," +
                            "ph_pres," +
                            "ph_grav_x,ph_grav_y,ph_grav_z," +
                            "sw_forward_w,sw_forward_x,sw_forward_y,sw_forward_z," +
                            "ph_forward_w,ph_forward_x,ph_forward_y,ph_forward_z," +
                            "sw_init_pres," +
                            "activity\n"
                )
                // Parse the file and path to uri
                Log.v(TAG, "Text file created at ${textFile.absolutePath}.")

                // begin the loop
                while (_imuStreamState) {

                    // Get data from smartwatch message queue
                    var swData = pollEntireQueue()
                    while (swData == null && _imuStreamState) {
                        delay(1L)
                        swData = pollEntireQueue()
                    }

                    // Get the newest phone IMU reading
                    var phoneData = composeImuMessage()
                    while (phoneData == null && _imuStreamState) {
                        delay(1L)
                        phoneData = composeImuMessage()
                    }

                    if (phoneData != null && swData != null) {
                        if (DataSingleton.calib_count < DataSingleton.SELF_CALIB_END) {
                            // calibrate the phone with the first data that comes in
                            _calibrationDatList.add(
                                phoneData.slice(5..8).toFloatArray()
                            )
                            DataSingleton.calib_count += 1

                            if (DataSingleton.calib_count == DataSingleton.SELF_CALIB_END) {
                                // set calibration vec when enough is available
                                DataSingleton.setWatchForwardQuat(
                                    swData.slice(23..26).toFloatArray()
                                )
                                DataSingleton.setPhoneForwardQuat(
                                    quatAverage(_calibrationDatList)
                                )
                                DataSingleton.setWatchRelPres(swData[27])
                                calibrationDat = DataSingleton.watchQuat.value +
                                        DataSingleton.phoneQuat.value +
                                        floatArrayOf(DataSingleton.watchPres.value)
                                Log.d(TAG, "finished self calib ${DataSingleton.calib_count}")
                                _calibrationDatList.clear()
                            } else {
                                continue
                            }
                        }
                        var wstr = ""
                        for (entry in swData.slice(0..22)
                            .toFloatArray() + phoneData + calibrationDat) {
                            wstr += "$entry,"
                        }
                        // new line at the end
                        fOut.write(wstr + "${DataSingleton.recordActivityLabel.value}\n")

                        _swOutCount += 1 // for Hz estimation
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e)
            _channelClient.close(c)
            Log.d(TAG, "IMU message recording stopped")
        }
    }

    /** reads watch IMU messages from _imuPpgQueue and broadcasts them via UDP */
    private suspend fun sendUdpImuMessages(c: ChannelClient.Channel) {
        try {
            // our constants for this loop
            val port = DataSingleton.imuPort.value
            val ip = DataSingleton.ip.value

            var calibrationDat = DataSingleton.watchQuat.value +
                    DataSingleton.phoneQuat.value +
                    floatArrayOf(DataSingleton.watchPres.value)

            withContext(Dispatchers.IO) {
                // open a socket
                val udpSocket = DatagramSocket(port)
                udpSocket.broadcast = true
                val socketInetAddress = InetAddress.getByName(ip)
                Log.v(TAG, "Opened UDP socket to $ip:$port")

                udpSocket.use {
                    // register all sensor listeners
                    for (l in _listeners) {
                        if (_sensorManager.getDefaultSensor(l.code) != null) {
                            _sensorManager.registerListener(
                                l,
                                _sensorManager.getDefaultSensor(l.code),
                                SensorManager.SENSOR_DELAY_FASTEST
                            )
                        } else {
                            throw Exception("Sensor code ${l.code} is not present on this device")
                        }
                    }

                    // begin the loop
                    while (_imuStreamState) {

                        // Get data from smartwatch message queue
                        var swData = pollEntireQueue()
                        while (swData == null && _imuStreamState) {
                            delay(1L)
                            swData = pollEntireQueue()
                        }

                        // Get the newest phone IMU reading
                        var phoneData = composeImuMessage()
                        while (phoneData == null && _imuStreamState) {
                            delay(1L)
                            phoneData = composeImuMessage()
                        }

                        if (phoneData != null && swData != null) {
                            if (DataSingleton.calib_count < DataSingleton.SELF_CALIB_END) {
                                // calibrate the phone with the first data that comes in
                                _calibrationDatList.add(
                                    phoneData.slice(5..8).toFloatArray()
                                )
                                DataSingleton.calib_count += 1

                                if (DataSingleton.calib_count == DataSingleton.SELF_CALIB_END) {
                                    // set calibration vec when enough is available
                                    DataSingleton.setWatchForwardQuat(
                                        swData.slice(23..26).toFloatArray()
                                    )
                                    DataSingleton.setPhoneForwardQuat(
                                        quatAverage(_calibrationDatList)
                                    )
                                    DataSingleton.setWatchRelPres(swData[27])
                                    calibrationDat = DataSingleton.watchQuat.value +
                                            DataSingleton.phoneQuat.value +
                                            floatArrayOf(DataSingleton.watchPres.value)
                                    Log.d(TAG, "finished self calib ${DataSingleton.calib_count}")
                                    _calibrationDatList.clear()
                                } else {
                                    continue
                                }
                            }


                            // write phone and watch data to buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.DUAL_IMU_MSG_SIZE)
                            // put smartwatch data
                            for (s in swData.slice(0..22)) {
                                buffer.putFloat(s)
                            }
                            // append phone data
                            for (v in phoneData) {
                                buffer.putFloat(v)
                            }
                            // append calibration data
                            for (v in calibrationDat) {
                                buffer.putFloat(v)
                            }
                            // create packet
                            val dp = DatagramPacket(
                                buffer.array(), buffer.capacity(), socketInetAddress, port
                            )
                            // finally, send via UDP
                            udpSocket.send(dp)
                            _swOutCount += 1 // for Hz estimation
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e)
            _channelClient.close(c)
            Log.d(TAG, "IMU UDP messages stopped")
        }
    }

    /**
     * if the swQueue has multiple entries, we want to summarize the acceleration and
     * gyro data of all entries
     */
    private fun pollEntireQueue(): FloatArray? {
        var lastRow = _swQueue.poll()

        // nothing to do if empty
        if (lastRow == null) {
            return null
        } else {
            // totals over all queued rows
            var totalT = 0F
            var totalAcc = floatArrayOf(0F, 0F, 0F)
            var totalGyr = floatArrayOf(0F, 0F, 0F)

            var rowBuf = ByteBuffer.wrap(lastRow)
            while (lastRow != null) {
                val dT = rowBuf.getFloat(0)
                totalT += dT // deltaT is first entry

                // sum gyro speed * delta T
                totalGyr = floatArrayOf(
                    totalGyr[0] + rowBuf.getFloat(10 * 4) * dT,
                    totalGyr[1] + rowBuf.getFloat(11 * 4) * dT,
                    totalGyr[2] + rowBuf.getFloat(12 * 4) * dT
                )

                // sum acc * delta T
                totalAcc = floatArrayOf(
                    totalAcc[0] + rowBuf.getFloat(16 * 4) * dT,
                    totalAcc[1] + rowBuf.getFloat(17 * 4) * dT,
                    totalAcc[2] + rowBuf.getFloat(18 * 4) * dT
                )

                // get next row
                lastRow = _swQueue.poll()
                if (lastRow != null) {
                    rowBuf = ByteBuffer.wrap(lastRow)
                }
            }

            // return as float array
            val floats = FloatArray(rowBuf.limit() / 4)
            rowBuf.asFloatBuffer().get(floats)

            // divide by total T to turn back into original measurement units
            floats[0] = totalT
            floats[10] = totalGyr[0] / totalT
            floats[11] = totalGyr[1] / totalT
            floats[12] = totalGyr[2] / totalT

            floats[16] = totalAcc[0] / totalT
            floats[17] = totalAcc[1] / totalT
            floats[18] = totalAcc[2] / totalT

            return floats
        }
    }

    private suspend fun swQueueFiller(c: ChannelClient.Channel) {
        try {
            // get the input stream from the opened channel
            val streamTask = _channelClient.getInputStream(c)
            val stream = Tasks.await(streamTask)
            stream.use {
                // begin the loop
                while (_imuStreamState) {
                    // if more than 0 bytes are available
                    if (stream.available() > 0) {
                        // read input stream message into buffer
                        val buffer = ByteBuffer.allocate(DataSingleton.IMU_MSG_SIZE).array()
                        stream.read(buffer)
                        _swQueue.add(buffer)
                        // for Hz estimation
                        _swInCount += 1
                    }
                    // the same interval with which the watch IMU service feeds into the stream
                    // bt data comes in in "bursts" pause for
                    delay(MSGBREAK)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e)
        } finally {
            _channelClient.close(c)
            Log.d(TAG, "IMU queue filler stopped")
        }
    }

    private fun onChannelOpen(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.IMU_PATH) {
            // set the local state to Streaming and start three loops
            _imuStreamState = true
            // First, start the coroutine to fill the queue with streamed watch data
            _scope.launch { swQueueFiller(c) }
            // Also, start the coroutine to deal with queued data and broadcast it via UDP
            if (DataSingleton.recordLocally.value) {
                _scope.launch { recordImuMessages(c) }
            } else {
                _scope.launch { sendUdpImuMessages(c) }
            }
            broadcastUiUpdate()
        }
    }

    private fun onChannelClose(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.IMU_PATH) {
            _imuStreamState = false
            _lastMsg = null
            _swQueue.clear()
            if (this::_sensorManager.isInitialized) {
                for (l in _listeners) {
                    _sensorManager.unregisterListener(l)
                }
            }
            broadcastUiUpdate()
        }
    }

    private fun composeImuMessage(): FloatArray? {
        // avoid composing a new message before receiving new data
        // also, this prevents division by 0 when averaging below
        if ((_tsDLacc == 0f) || (_tsDGyro == 0f) || _rotvec.contentEquals(
                floatArrayOf(1f, 0f, 0f, 0f, 0f)
            )
        ) {
            return null
        }

        // get time stamp as float array to ease parsing
        val tsNow = LocalDateTime.now()
        val ts = floatArrayOf(
            tsNow.hour.toFloat(),
            tsNow.minute.toFloat(),
            tsNow.second.toFloat(),
            tsNow.nano.toFloat()
        )

        // estimate delta time between messages
        var dT = 1.0F
        if (_lastMsg == null) {
            _lastMsg = tsNow
        } else {
            // avoid over-amplification by clamping dT at 1
            dT = min(Duration.between(_lastMsg, tsNow).toNanos() * NS2S, dT)
            _lastMsg = tsNow
        }

        // average gyro velocities
        val tgyro = floatArrayOf(
            _dGyro[0] / _tsDGyro,
            _dGyro[1] / _tsDGyro,
            _dGyro[2] / _tsDGyro
        )

        // average accelerations
        val tLacc = floatArrayOf(
            _dpLvel[0] / _tsDLacc,
            _dpLvel[1] / _tsDLacc,
            _dpLvel[2] / _tsDLacc
        )

        // compose the message as a float array
        val message = floatArrayOf(dT) + // [0] delta time since last message
                ts + // [1,2,3,4] actual time stamp
                _rotvec + // transformed rotation vector[5,6,7,8,9] is a quaternion [w,x,y,z, conf]
                tgyro + // mean gyro
                _dpLvel + // [3] integrated linear acc x,y,z
                tLacc + // mean acc
                _pres + // [1] atmospheric pressure
                _grav  // [3] vector indicating the direction of gravity x,y,z

        // now that the message is stored, reset the deltas
        // translation vel
        _dpLvel = floatArrayOf(0f, 0f, 0f)
        _tsDLacc = 0f

        // rotation vel
        _dGyro = floatArrayOf(0f, 0f, 0f)
        _tsDGyro = 0f

        // replace rot vec with default to be overwritten when new value comes in
        _rotvec = floatArrayOf(1f, 0f, 0f, 0f, 0f)

        return message
    }

    /** sensor callbacks (Events) */
    fun onLaccReadout(newReadout: SensorEvent) {
        if (_tsLacc != 0L) {
            // get time difference in seconds
            val dT: Float = (newReadout.timestamp - _tsLacc) * NS2S
            // avoid over-amplifying. If the time difference is larger than a second,
            // something in the pipeline must be on pause
            if (dT > 1f) {
                _dpLvel = newReadout.values
                _tsDLacc = 1f
            } else {
                // integrate
                _dpLvel[0] += newReadout.values[0] * dT
                _dpLvel[1] += newReadout.values[1] * dT
                _dpLvel[2] += newReadout.values[2] * dT
                // also keep total time to estimate simple mean by division
                _tsDLacc += dT
            }
        }
        _tsLacc = newReadout.timestamp
    }

    fun onGyroReadout(newReadout: SensorEvent) {
        // see above documentation in Lacc readout for the rationale of individual code lines
        if (_tsGyro != 0L) {
            val dT: Float = (newReadout.timestamp - _tsGyro) * NS2S
            if (dT > 1f) {
                _dGyro = newReadout.values
                _tsDGyro = 1f
            } else {
                _dGyro[0] += newReadout.values[0] * dT
                _dGyro[1] += newReadout.values[1] * dT
                _dGyro[2] += newReadout.values[2] * dT
                _tsDGyro += dT
            }
        }
        _tsGyro = newReadout.timestamp
    }

    fun onRotVecReadout(newReadout: SensorEvent) {
        // newReadout is [x,y,z,w, confidence]
        // our preferred order is [w,x,y,z, confidence]
        // This is not important for state transition.
        // No averaging over time needed, because we are only interested in the most
        // recent observation
        _rotvec = floatArrayOf(
            newReadout.values[3],
            newReadout.values[0],
            newReadout.values[1],
            newReadout.values[2],
            newReadout.values[4]
        )
    }

    fun onPressureReadout(newReadout: SensorEvent) {
        _pres = newReadout.values
    }

    fun onGravReadout(newReadout: SensorEvent) {
        _grav = newReadout.values
    }

    override fun onBind(p0: Intent?): IBinder? {
        // not intended to be bound
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        _imuStreamState = false
        _scope.cancel()
        for (l in _listeners) {
            _sensorManager.unregisterListener(l)
        }
        _channelClient.unregisterChannelCallback(_channelCallback)
        Log.v(TAG, "IMU Service destroyed")
    }
}
