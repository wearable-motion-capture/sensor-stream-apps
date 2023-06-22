package com.mocap.watch.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.SensorListener
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
import java.time.LocalDateTime


class UdpImuService : Service() {

    companion object {
        private const val TAG = "UDP IMU Service"  // for logging
        private const val NS2S = 1.0f / 1000000000.0f //Nano second to second
        private const val MSGBREAK = 5L
    }

    private lateinit var _sensorManager: SensorManager // to be set in onCreate
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private var _imuStreamState = false

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

    // listeners to register and unregister them automatically on service start and stop
    private var _listeners = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { onPressureReadout(it) }, // atmospheric pressure
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { onLaccReadout(it) }, // linear acceleration (without gravity) in watch coordinates
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { onRotVecReadout(it) }, // global (respect to North and down vec) watch orientation as a quaternion
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { onGravReadout(it) }, // gravity direction in watch coordinate system
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { onGyroReadout(it) } // angular velocities in watch coordinate system
    )

    override fun onCreate() {
        _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    /**
     * Stream IMU data in a while loop.
     * This function can be suspended to be able to kill the loop by stopping or pausing
     * the scope it was started from.
     */
    private suspend fun susStreamData() {

        val ip = DataSingleton.IP.value
        val port = DataSingleton.UDP_IMU_PORT

        withContext(Dispatchers.IO) {
            try {
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

                    // start the stream loop
                    _imuStreamState = true // this value may get changed elsewhere to stop
                    while (_imuStreamState) {
                        // compose message
                        val lastDat = composeImuMessage()
                        // only process if a message was composed successfully
                        if (lastDat != null) {
                            // feed into byte buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.IMU_UDP_MSG_SIZE)
                            for (v in lastDat) buffer.putFloat(v)
                            val dp = DatagramPacket(
                                buffer.array(), buffer.capacity(), socketInetAddress, port
                            )
                            udpSocket.send(dp)  // finally, send the byte stream
                        }
                        // avoid sending too fast. Delay coroutine for milliseconds
                        delay(MSGBREAK)
                    }
                }
            } catch (e: Exception) {
                // if the loop ends of was disrupted, close everything and reset
                Log.w(TAG, e.message.toString())
            } finally {
                _imuStreamState = false
                for (l in _listeners) {
                    _sensorManager.unregisterListener(l)
                }
                Log.d(TAG, "IMU stream stopped")
                onDestroy() // stop service
            }
        }
    }


    /**
     * Triggers the streaming of IMU data as a service
     * or (if already running) stops the service.
     */
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        if (_imuStreamState) {
            Log.w(TAG, "stream already started")
            _imuStreamState = false
            onDestroy() // stop service
        } else {
            _scope.launch { susStreamData() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _imuStreamState = false
        _scope.cancel()
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) {
                _sensorManager.unregisterListener(l)
            }
        }
        val intent = Intent(DataSingleton.BROADCAST_CLOSE)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.IMU_UDP_PATH)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    /** not intended to be bound **/
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun composeImuMessage(): FloatArray? {
        // get calibration values
        val press = DataSingleton.CALIB_PRESS.value
        val north = DataSingleton.CALIB_NORTH.value.toFloat()

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
                _grav + // [3] vector indicating the direction of gravity x,y,z
                floatArrayOf(
                    press, // initial atmospheric pressure collected during calibration
                    north // body orientation in relation to magnetic north pole collected during calibration
                )

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
}