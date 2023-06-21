package com.mocap.phone.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.round

class ImuService : Service() {

    companion object {
        private const val TAG = "Channel IMU Service"  // for logging
        private const val NS2S = 1.0f / 1000000000.0f //Nano second to second
        private const val MS2S = 0.001f
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
    private var _rotvec: FloatArray = floatArrayOf(1f, 0f, 0f, 0f) // rot vector as [w,x,y,z] quat

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
                broadcastUpdate()
                delay(2000L)
            }
        }
        Log.v(TAG, "Service started")
        return START_NOT_STICKY
    }

    /** Broadcasts service status values to view model to update the UI */
    private fun broadcastUpdate() {
        // duration estimate in seconds for Hz
        val now = LocalDateTime.now()
        val diff = Duration.between(_lastBroadcast, now)
        val ds = diff.toMillis() * MS2S
        _lastBroadcast = LocalDateTime.now()

        val intent = Intent(DataSingleton.BROADCAST_UPDATE)
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_KEY,
            DataSingleton.IMU_CHANNEL_PATH
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_STATE,
            _imuStreamState
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_HZ_IN,
            round(_swInCount.toFloat() / ds)
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_HZ_OUT,
            round(_swOutCount.toFloat() / ds)
        )
        intent.putExtra(
            DataSingleton.BROADCAST_SERVICE_QUEUE,
            _swQueue.count()
        )
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        _swInCount = 0
        _swOutCount = 0
    }

    /** reads watch IMU messages from _imuPpgQueue and broadcasts them via UDP */
    private suspend fun sendUdpImuMessages(c: ChannelClient.Channel) {
        try {
            // our constants for this loop
            val port = DataSingleton.UDP_IMU_PORT
            val ip = DataSingleton.ip.value

            val calibratedDat = DataSingleton.watchQuat.value +
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
                        _sensorManager.registerListener(
                            l,
                            _sensorManager.getDefaultSensor(l.code),
                            SensorManager.SENSOR_DELAY_FASTEST
                        )
                    }

                    // begin the loop
                    while (_imuStreamState) {

                        // get data from queue
                        // skip entries that are already old
                        var swData = _swQueue.poll()
                        while (_swQueue.count() > 10) {
                            swData = _swQueue.poll()
                        }

                        // if we got some data from the watch...
                        if (swData != null) {

                            // ... also get the newest phone IMU reading
                            var phoneData = composeImuMessage()
                            while (phoneData == null) {
                                phoneData = composeImuMessage()
                                delay(1L)
                            }

                            // write phone and watch data to buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.DUAL_IMU_MSG_SIZE)
                            // put smartwatch data
                            buffer.put(swData)
                            // append phone data
                            for (v in phoneData) {
                                buffer.putFloat(v)
                            }
                            // append calibration data
                            for (v in calibratedDat) {
                                buffer.putFloat(v)
                            }
                            // create packet
                            val dp = DatagramPacket(
                                buffer.array(),
                                buffer.capacity(),
                                socketInetAddress,
                                port
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
        } finally {
            _imuStreamState = false
            _channelClient.close(c)
            for (l in _listeners) {
                _sensorManager.unregisterListener(l)
            }
            Log.d(TAG, "IMU stream stopped")
        }
    }

    private fun swQueueFiller(c: ChannelClient.Channel) {
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
                        val buffer = ByteBuffer.allocate(DataSingleton.IMU_MSG_SIZE)
                        stream.read(buffer.array(), 0, buffer.capacity())
                        _swQueue.add(buffer.array())
                        // for Hz estimation
                        _swInCount += 1
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e)
        } finally {
            _imuStreamState = false
            _channelClient.close(c)
            _swQueue.clear()
        }
    }

    private fun onChannelOpen(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.IMU_CHANNEL_PATH) {
            // set the local state to Streaming and start three loops
            _imuStreamState = true
            // First, start the coroutine to fill the queue with streamed watch data
            _scope.launch { swQueueFiller(c) }
            // Also, start the coroutine to deal with queued data and broadcast it via UDP
            _scope.launch { sendUdpImuMessages(c) }
            broadcastUpdate()
        }
    }

    private fun onChannelClose(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.IMU_CHANNEL_PATH) {
            if (_imuStreamState) {
                _imuStreamState = false
            }
            broadcastUpdate()
        }
    }

    private fun composeImuMessage(): FloatArray? {
        // avoid composing a new message before receiving new data
        // also, this prevents division by 0 when averaging below
        if (
            (_tsDLacc == 0f) ||
            (_tsDGyro == 0f) ||
            _rotvec.contentEquals(
                floatArrayOf(1f, 0f, 0f, 0f)
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
        val message = ts +
                _rotvec + // transformed rotation vector[4] is a quaternion [w,x,y,z]
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
        _rotvec = floatArrayOf(1f, 0f, 0f, 0f)

        return message
    }

    /** sensor callbacks (Events) */
    fun onLaccReadout(newReadout: SensorEvent) {
        if (_tsLacc != 0L) {
            // get time difference in seconds
            val dT: Float = (newReadout.timestamp - _tsGyro) * NS2S
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
        // our preferred order is [w,x,y,z]
        // This is not important for state transition.
        // No averaging over time needed, because we are only interested in the most
        // recent observation
        _rotvec = floatArrayOf(
            newReadout.values[3], newReadout.values[0], newReadout.values[1], newReadout.values[2]
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
        _channelClient.unregisterChannelCallback(_channelCallback)
        Log.v(TAG, "Service destroyed")
    }
}