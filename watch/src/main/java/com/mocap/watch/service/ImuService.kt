package com.mocap.watch.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
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


class ImuService : Service() {

    companion object {
        private const val TAG = "IMU Service"  // for logging
        private const val IMU_STREAM_INTERVAL = DataSingleton.STREAM_INTERVAL
    }

    private lateinit var _sensorManager: SensorManager
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private var _imuStreamState = false

    // callbacks will write to these variables
    private var _rotVec: FloatArray = FloatArray(4) // Rotation Vector sensor or estimation
    private var _lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var _accl: FloatArray = FloatArray(3) // raw acceleration
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var _gyro: FloatArray = FloatArray(3) // gyroscope
    private var _magn: FloatArray = FloatArray(3) // magnetic

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
        ) { onGyroReadout(it) },
//        SensorListener(
//            Sensor.TYPE_HEART_RATE
//        ) { globalState.onHrReadout(it) }
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
                            // compose message as float array
                            val sensorData =
                                _rotVec + // transformed rotation vector[4] is a quaternion [w,x,y,z]
                                        _lacc + // [3] linear acceleration x,y,z
                                        _pres + // [1] atmospheric pressure
                                        _grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                                        _gyro // [3] gyro data for time series prediction

                            // feed into byte buffer
                            val buffer = ByteBuffer.allocate(4 * DataSingleton.IMU_MSG_SIZE)
                            for (v in sensorData) buffer.putFloat(v)

                            // write to output stream
                            outputStream.write(buffer.array(), 0, buffer.capacity())
                            delay(IMU_STREAM_INTERVAL)
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
            return START_NOT_STICKY
        }
        streamTrigger(sourceId)
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

    /** not intended to be bound **/
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Events
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

    fun onPressureReadout(newReadout: FloatArray) {
        _pres = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }
}