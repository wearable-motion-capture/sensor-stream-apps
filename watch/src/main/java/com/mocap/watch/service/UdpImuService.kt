package com.mocap.watch.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue


class UdpImuService : Service() {

    companion object {
        private const val TAG = "IMU Service"  // for logging
    }

    private lateinit var _sensorManager: SensorManager
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private var _imuStreamState = false

    // callbacks will write to these variables
    private var _lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var _accl: FloatArray = FloatArray(3) // raw acceleration
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var _gyro: FloatArray = FloatArray(3) // gyroscope
    private var _magn: FloatArray = FloatArray(3) // magnetic

    // the onRotVecReadout will fill this queue with measurements
    // the streamTrigger Coroutine will channel them to the phone
    private var _imuQueue = ConcurrentLinkedQueue<FloatArray>()

    // store listeners in this list to register and unregister them automatically
    private var _listeners = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
        SensorListener(
            Sensor.TYPE_ACCELEROMETER
        ) { onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
        ) { onMagnReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { onGravReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { onGyroReadout(it) }
    )

    override fun onCreate() {
        // access and observe sensors
        _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private fun streamTrigger() {
        if (_imuStreamState) {
            Log.w(TAG, "stream already started")
            stopSelf()
            return
        } else {
            _scope.launch {
                susStreamTrigger()
            }
        }
    }

    private suspend fun susStreamTrigger() {
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

                    for (l in _listeners) {
                        _sensorManager.registerListener(
                            l,
                            _sensorManager.getDefaultSensor(l.code),
                            SensorManager.SENSOR_DELAY_FASTEST
                        )
                    }

                    // start the stream loop
                    _imuStreamState = true
                    while (_imuStreamState) {

                        // get data from queue
                        // skip entries that are already old. This only happens if the watch
                        // processes incoming measurements too slowly
                        var lastDat = _imuQueue.poll()
                        while (_imuQueue.count() > 10) {
                            lastDat = _imuQueue.poll()
                        }
                        if (lastDat != null) {

                            // get time stamp as float array to ease parsing
                            val dt = LocalDateTime.now()
                            val ts = floatArrayOf(
                                dt.hour.toFloat(),
                                dt.minute.toFloat(),
                                dt.second.toFloat(),
                                dt.nano.toFloat()
                            )

                            // feed into byte buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.IMU_UDP_MSG_SIZE)
                            for (v in (ts + lastDat)) buffer.putFloat(v)

                            val dp = DatagramPacket(
                                buffer.array(),
                                buffer.capacity(),
                                socketInetAddress,
                                port
                            )
                            // finally, send the byte stream
                            udpSocket.send(dp)
                        }
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
                stopSelf() // stop service
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        streamTrigger()
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

    // Events
    /** sensor callbacks */
    // Individual sensor reads are triggered by their onValueChanged events
    fun onRotVecReadout(newReadout: FloatArray) {

        val press = DataSingleton.CALIB_PRESS.value
        val north = DataSingleton.CALIB_NORTH.value.toFloat()

        // newReadout is [x,y,z,w, confidence]
        // our preferred order system is [w,x,y,z]
        val rotVec = floatArrayOf(
            newReadout[3],
            newReadout[0],
            newReadout[1],
            newReadout[2]
        )

        _imuQueue.add(
            rotVec + // transformed rotation vector[4] is a quaternion [w,x,y,z]
                    _lacc + // [3] linear acceleration x,y,z
                    _pres + // [1] atmospheric pressure
                    _grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                    _gyro + // [3] gyro data for time series prediction)
                    floatArrayOf(
                        press, // initial atmospheric pressure collected during calibration
                        north // body orientation in relation to magnetic north pole collected during calibration
                    )
        )
    }

    fun onLaccReadout(newReadout: FloatArray) {
        _lacc = newReadout
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

    fun onPressureReadout(newReadout: FloatArray) {
        _pres = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }
}