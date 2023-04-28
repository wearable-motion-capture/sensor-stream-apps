package com.mocap.watch.activity

import android.hardware.SensorManager
import android.os.*
import androidx.activity.ComponentActivity
import com.mocap.watch.modules.SensorListener

/**
 * This is an open class for all activities that access sensor values
 */
open class SensorActivity : ComponentActivity() {

    private lateinit var _sensorManager: SensorManager

    // store listeners in this list to register and unregister them automatically
    private var _listeners = listOf<SensorListener>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // access and observe sensors
        _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

//            list all available sensors
//            //getSensorList(Sensor.TYPE_ALL) lists all the sensors present in the device
//            val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
//            for (device in deviceSensors) {
//              println(device.toString())
//            }

    }

    protected fun setSensorListeners(listenerList: List<SensorListener>) {
        _listeners = listenerList
        registerSensorListeners()
    }

    /**
     * Called on app startup and whenever app resumes
     */
    private fun registerSensorListeners() {
        // register all listeners with their assigned codes
        for (l in _listeners) {
            val check = _sensorManager.registerListener(
                l,
                _sensorManager.getDefaultSensor(l.code),
                SensorManager.SENSOR_DELAY_FASTEST
            )
            if (check) {
                println("device has %s".format(l.code))
            }
        }
    }

    /**
     * Re-register SensorListeners when app starts up from background
     */
    override fun onResume() {
        super.onResume()
        if (!this::_sensorManager.isInitialized) {
            return
        } else {
            registerSensorListeners()
        }
    }

    /**
     * Unregister SensorListeners when app is in background
     */
    override fun onPause() {
        super.onPause()
        if (!this::_sensorManager.isInitialized) {
            return
        } else {
            // unregister listeners
            for (l in _listeners) {
                _sensorManager.unregisterListener(l)
            }
        }
    }
}
