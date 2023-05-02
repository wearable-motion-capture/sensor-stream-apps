package com.mocap.phone.activity

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mocap.phone.modules.SensorListener
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderCalib
import com.mocap.phone.viewmodel.CalibViewModel


class CalibActivity : ComponentActivity() {

    companion object {
        private const val TAG = "Calibration"  // for logging
    }

    private lateinit var _viewModel: CalibViewModel
    private lateinit var _vibrator: Vibrator
    private lateinit var _listener: SensorListener
    private lateinit var _sensorManager: SensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // get the vibrator service.
            //  This has been updated in SDK 31..
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                _vibrator = vibratorManager.defaultVibrator
            } else {
                // we require the compatibility with SDK 20 for our galaxy watch 4
                _vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            // with the vibration service, create the view model
            _viewModel = CalibViewModel(this.application, _vibrator)

            // with the view model, create the sensor listener
            _listener = SensorListener(Sensor.TYPE_ROTATION_VECTOR) {
                _viewModel.onRotVecReadout(it)
            }

            // register listener with sensor manager
            _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            registerSensorListeners()

            // keep screen on to not enter ambient mode
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // check if a source node ID was sent with the application
            val sourceId: String? = intent.extras?.getString("sourceNodeId")


            PhoneTheme {
                RenderCalib(quatReadingStateFlow = _viewModel.quatReading)

                // begin the calibration
                _viewModel.calibrationTrigger(
                    doneCallback = this::onComplete,
                    sourceId = sourceId
                )
            }
        }
    }

    /**
     * Register all listeners with their assigned codes.
     * Called on app startup and whenever app resumes
     */
    private fun registerSensorListeners() {
        if (this::_sensorManager.isInitialized && this::_listener.isInitialized) {
            _sensorManager.registerListener(
                _listener,
                _sensorManager.getDefaultSensor(_listener.code),
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    /**
     * Unregister listeners and cancel vibration signals when exiting
     * the calibration activity
     */
    private fun onComplete() {
        if (this::_vibrator.isInitialized) {
            _vibrator.cancel()
        }
        if (this::_sensorManager.isInitialized && this::_listener.isInitialized) {
            _sensorManager.unregisterListener(_listener)
        }
        this.finish()
    }

    override fun onResume() {
        super.onResume()
        registerSensorListeners()
    }

    override fun onPause() {
        super.onPause()
        onComplete()
    }
}
