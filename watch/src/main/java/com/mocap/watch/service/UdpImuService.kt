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
        private const val TAG = "UDP IMU Service"  // for logging
        private const val NS2S = 1.0f / 1000000000.0f //Nano second to second
    }

    private lateinit var _sensorManager: SensorManager // to be set in onCreate
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private var _imuStreamState = false

    // callbacks will write to these variables
    private var _dpLvel: FloatArray = floatArrayOf(0f, 0f, 0f) // integrated linear acc (no gravity)
//  private var _dpLpos: FloatArray = floatArrayOf(0f, 0f, 0f) // 2x integrated linear acc
    private var _tsLacc: Long = 0 // in nano seconds

//  private var _accl: FloatArray = floatArrayOf(0f, 0f, 0f) // raw acceleration
//  private var _tsAcc = LocalDateTime.now() // time stamp to measure delta t
//  private var _magn: FloatArray = FloatArray(3) // magnetic
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)

    // delta quat from gyro as [w,x,y,z] initialized as identity quat
    private var _dGyro: FloatArray = floatArrayOf(1f, 0f, 0f, 0f)
    private var _tsGyro: Long = 0
    private var _tsDGyro: Float = 0f

    // the onRotVecReadout will fill this queue with measurements
    // the susStreamData Coroutine will stream them
    private var _imuQueue = ConcurrentLinkedQueue<FloatArray>()

    // listeners to register and unregister them automatically on service start and stop
    private var _listeners = listOf(
        SensorListener(
            Sensor.TYPE_PRESSURE
        ) { onPressureReadout(it) },
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_GRAVITY
        ) { onGravReadout(it) }, SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { onGyroReadout(it) }
//        SensorListener(
//            Sensor.TYPE_ACCELEROMETER
//        ) { onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
//        SensorListener(
//            Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
//        ) { onMagnReadout(it) },
    )

    override fun onCreate() {
        _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    /**
     * Stream IMU data in a while loop.
     * This function is suspendible to be able to kill the loop by stopping or pausing
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
                    // the loop
                    while (_imuStreamState) {

                        // get data from queue
                        var lastDat = _imuQueue.poll()
                        // skip entries that are already old. This only a lifeline to handle cases
                        // where the watch becomes too slow and processes incoming measurements
                        // too slowly
                        while (_imuQueue.count() > 10) {
                            lastDat = _imuQueue.poll()
                        }
                        if (lastDat != null) {
                            // feed into byte buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.IMU_UDP_MSG_SIZE)
                            for (v in lastDat) buffer.putFloat(v)

                            val dp = DatagramPacket(
                                buffer.array(), buffer.capacity(), socketInetAddress, port
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


    /**
     * Triggers the streaming of IMU data as a service
     * or (if already running) stops the service.
     */
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        if (_imuStreamState) {
            Log.w(TAG, "stream already started")
            stopSelf()
            return START_NOT_STICKY
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

    // Events
    /** sensor callbacks
     * Individual sensor reads are triggered by their onValueChanged events. The onRotVecReadout is
     * the main trigger, which then collects delta rotation an translation values estimated through
     * gyro and lacc readouts.
     */
    fun onRotVecReadout(newReadout: SensorEvent) {
        val values = newReadout.values
        val press = DataSingleton.CALIB_PRESS.value
        val north = DataSingleton.CALIB_NORTH.value.toFloat()

        // get time stamp as float array to ease parsing
        val tsNow = LocalDateTime.now()
        val ts = floatArrayOf(
            tsNow.hour.toFloat(),
            tsNow.minute.toFloat(),
            tsNow.second.toFloat(),
            tsNow.nano.toFloat()
        )

        // newReadout is [x,y,z,w, confidence]
        // our preferred order is [w,x,y,z]
        val rotVec = floatArrayOf(
            values[3], values[0], values[1], values[2]
        )

        // average gyro velocities
        val tgyro = floatArrayOf(
            _dGyro[0] / _tsDGyro,
            _dGyro[1] / _tsDGyro,
            _dGyro[2] / _tsDGyro
        )

        _imuQueue.add(
            ts +
                    rotVec + // transformed rotation vector[4] is a quaternion [w,x,y,z]
                    tgyro + // mean gyro
                    _dpLvel + // [3] integrated linear acc x,y,z
                    _pres + // [1] atmospheric pressure
                    _grav + // [3] vector indicating the direction of gravity x,y,z
                    floatArrayOf(
                        press, // initial atmospheric pressure collected during calibration
                        north // body orientation in relation to magnetic north pole collected during calibration
                    )
        )
        // now that the time step is stored, reset the deltas
        _dGyro = floatArrayOf(1f, 0f, 0f, 0f) // rotation vel
        _tsDGyro = 0f
        _dpLvel = floatArrayOf(0f, 0f, 0f) // translation vel
    }

    fun onLaccReadout(newReadout: SensorEvent) {
        if (_tsLacc != 0L) {
            // get time difference in seconds
            val dT: Float = (newReadout.timestamp - _tsGyro) * NS2S
            // avoid over-amplifying. If the time difference is larger than a second,
            // something in the pipeline must be on pause
            if (dT > 1f) {
                _dpLvel = newReadout.values
            }
            // integrate to obtain velocity
            _dpLvel[0] += newReadout.values[0] * dT
            _dpLvel[1] += newReadout.values[1] * dT
            _dpLvel[2] += newReadout.values[2] * dT
        }
        _tsLacc = newReadout.timestamp
    }

    fun onGyroReadout(newReadout: SensorEvent) {
        // This time step's delta rotation to be multiplied by the current rotation
        // after computing it from the gyro sample data.
        if (_tsGyro != 0L) {
            // get time difference in seconds
            val dT: Float = (newReadout.timestamp - _tsGyro) * NS2S
            val gyro = newReadout.values
            // avoid over-amplifying. If the time difference is larger than a second,
            // something in the pipeline must be on pause
            if (dT > 1f) {
                _dGyro = gyro
                _tsDGyro = 1f
            } else {
                _dGyro[0] += gyro[0] * dT
                _dGyro[1] += gyro[1] * dT
                _dGyro[2] += gyro[2] * dT
                _tsDGyro += dT
            }
        }
        _tsGyro = newReadout.timestamp

    }

//    fun onGyroReadout(newReadout: SensorEvent) {
//        // This time step's delta rotation to be multiplied by the current rotation
//        // after computing it from the gyro sample data.
//        if (_tsGyro != 0L) {
//            // get time difference in seconds
//            var dT: Float = (newReadout.timestamp - _tsGyro) * NS2S
//
//            // avoid over-amplifying. If the time difference is larger than a second,
//            // something in the pipeline must be on pause
//            if (dT > 1f) {
//                dT = 1f
//            }
//
//            // Axis of the rotation sample, not normalized yet.
//            var axisX: Float = newReadout.values[0]
//            var axisY: Float = newReadout.values[1]
//            var axisZ: Float = newReadout.values[2]
//
//            // Calculate the angular speed of the sample
//            val omegaMagnitude: Float = kotlin.math.sqrt(
//                axisX * axisX + axisY * axisY + axisZ * axisZ
//            )
//
//            // Normalize the rotation vector if it's big enough to get the axis
//            if (omegaMagnitude > kotlin.math.E) {
//                axisX /= omegaMagnitude
//                axisY /= omegaMagnitude
//                axisZ /= omegaMagnitude
//            }
//
//            // Integrate around this axis with the angular speed by the time step
//            // in order to get a delta rotation from this sample over the time step
//            // We will convert this axis-angle representation of the delta rotation
//            // into a quaternion
//            val thetaOverTwo = omegaMagnitude * dT / 2.0f
//            val sinThetaOverTwo: Float = kotlin.math.sin(thetaOverTwo)
//            val deltaVec = floatArrayOf(
//                kotlin.math.cos(thetaOverTwo), // w
//                sinThetaOverTwo * axisX, // x
//                sinThetaOverTwo * axisY, // y
//                sinThetaOverTwo * axisZ  // z
//            )
//            _dqGyro = hamiltonProd(_dqGyro, deltaVec)
//        }
//        _tsGyro = newReadout.timestamp
//
//    }



    fun onPressureReadout(newReadout: SensorEvent) {
        _pres = newReadout.values
    }

    fun onGravReadout(newReadout: SensorEvent) {
        _grav = newReadout.values
    }

//    fun onMagnReadout(newReadout: SensorEvent) {
//        _magn = newReadout.values
//    }
//    fun onAcclReadout(newReadout: SensorEvent) {
//        _accl = newReadout.values
//    }
//
//    /**
//     * Hamilton product to multiply two quaternions
//     */
//    fun hamiltonProd(a: FloatArray, b: FloatArray): FloatArray {
//        // this is the result of H(a,b)
//        return floatArrayOf(
//            a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
//            a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
//            a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
//            a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]
//        )
//    }
}