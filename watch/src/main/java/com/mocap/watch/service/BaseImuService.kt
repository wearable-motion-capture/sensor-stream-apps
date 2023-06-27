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
import java.time.LocalDateTime

abstract class BaseImuService : Service() {

    companion object {
        private const val TAG = "IMU service"
        private const val NS2S = 1.0f / 1000000000.0f //Nano second to second
        const val MSGBREAK = 5L // minimum pause between messages
    }

    private lateinit var _sensorManager: SensorManager // to be set in onCreate

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

    protected val scope = CoroutineScope(Job() + Dispatchers.IO)
    protected var imuStreamState = false

    // listeners to register and unregister them automatically on service start and stop
    private val _listeners = listOf(
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

    override fun onDestroy() {
        super.onDestroy()
        stopService()
        val intent = Intent(DataSingleton.BROADCAST_CLOSE)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.IMU_UDP_PATH)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        scope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    /** not intended to be bound **/
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    protected fun registerSensorListeners() {
        // make sure we can register sensor listeners
        if (!this::_sensorManager.isInitialized) {
            throw Exception("Attempted to register listeners but sensor manager is not initialized")
        }
        // register all sensor listeners
        for (l in _listeners) {
            _sensorManager.registerListener(
                l,
                _sensorManager.getDefaultSensor(l.code),
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    protected fun stopService() {
        imuStreamState = false
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) {
                _sensorManager.unregisterListener(l)
            }
        }
        Log.d(TAG, "Service finished")
    }

    protected fun composeImuMessage(): FloatArray? {
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
//            Log.d(
//                TAG, "data is null " +
//                        "(_tsDLacc = $_tsDLacc, _tsDGyro = $_tsDGyro, _rotvec = $_rotvec)"
//            )
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
