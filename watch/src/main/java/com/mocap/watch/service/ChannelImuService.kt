package com.mocap.watch.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.SensorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.time.LocalDateTime


class ChannelImuService : Service() {

    companion object {
        private const val TAG = "Channel IMU Service"  // for logging
        private const val NS2S = 1.0f / 1000000000.0f //Nano second to second
        private const val MSGBREAK = 1L
    }

    private lateinit var _sensorManager: SensorManager
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
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
    //private var _rotvecQueue = mutableListOf<FloatArray>()


    // store listeners in this list to register and unregister them automatically
    private var _listeners = listOf(
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
        // access and observe sensors
        _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private fun streamTrigger(nodeId: String) {
        if (_imuStreamState) {
            Log.w(TAG, "stream already started")
            stopSelf()
            return
        }
        _scope.launch {
            try {
                // Open the channel
                val channel = _channelClient.openChannel(
                    nodeId,
                    DataSingleton.IMU_CHANNEL_PATH
                ).await()
                Log.d(TAG, "Opened ${DataSingleton.IMU_CHANNEL_PATH} to $nodeId")

                try {
                    // get output stream
                    val outputStream = _channelClient.getOutputStream(channel).await()
                    // get output stream
                    outputStream.use {
                        // register all listeners with their assigned codes
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

                            // compose message
                            var lastDat = composeImuMessage()
                            while (lastDat == null){
                                delay(MSGBREAK)
                                lastDat = composeImuMessage()
                            }

                            // only process if a message was composed successfully
                            // feed into byte buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.IMU_CHANNEL_MSG_SIZE)
                            for (v in lastDat) buffer.putFloat(v)
                            // write to output stream
                            outputStream.write(buffer.array(), 0, buffer.capacity())
                        }
                    }
                } catch (e: Exception) {
                    // In case the channel gets destroyed while still in the loop
                    Log.d(TAG, e.message.toString())
                } finally {
                    _imuStreamState = false
                    for (l in _listeners) {
                        _sensorManager.unregisterListener(l)
                    }
                    Log.d(TAG, "IMU stream stopped")
                    _channelClient.close(channel)
                    stopSelf() // stop service
                }
            } catch (e: Exception) {
                // In case the channel gets destroyed while still in the loop
                Log.w(TAG, "Channel failed" + e.message.toString())
                stopSelf() // stop service
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        val sourceId = intent.extras?.getString("sourceNodeId")
        if (sourceId == null) {
            Log.w(TAG, "no Node ID given")
            stopSelf()
        } else {
            streamTrigger(sourceId)
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
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.IMU_CHANNEL_PATH)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    // Events
    /** sensor callbacks */
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
                _grav // [3] vector indicating the direction of gravity x,y,z

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
        // recent observation
        _rotvec = floatArrayOf(
                newReadout.values[3],
                newReadout.values[0],
                newReadout.values[1],
                newReadout.values[2]
            )
    }

    fun onPressureReadout(newReadout: SensorEvent) {
        _pres = newReadout.values
    }

    fun onGravReadout(newReadout: SensorEvent) {
        _grav = newReadout.values
    }

    override fun onBind(intent: Intent?): IBinder? {
        // not intended to be bound
        return null
    }
}