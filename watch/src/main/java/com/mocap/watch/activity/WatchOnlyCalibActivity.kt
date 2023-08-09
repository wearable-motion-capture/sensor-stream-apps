package com.mocap.watch.activity

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mocap.watch.modules.SensorListener
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderStandaloneCalib
import com.mocap.watch.viewmodel.StandaloneCalibViewModel


class WatchOnlyCalibActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WatchOnlyCalibration"  // for logging
    }

    private lateinit var _viewModel: StandaloneCalibViewModel
    private lateinit var _vibrator: Vibrator
    private var _listeners = listOf<SensorListener>()
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

            // create the view model class
            _viewModel = StandaloneCalibViewModel(
                application = this.application,
                vibrator = _vibrator,
                onCompleteCallback = this::onComplete
            )

            // add Sensor Listeners with our calibrator callbacks
            _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            _listeners = listOf(
                SensorListener(
                    Sensor.TYPE_PRESSURE
                ) { _viewModel.onPressureReadout(it) },
                SensorListener(
                    Sensor.TYPE_ROTATION_VECTOR
                ) { _viewModel.onRotVecReadout(it) },
                SensorListener(
                    Sensor.TYPE_GRAVITY
                ) { _viewModel.onGravReadout(it) }
            )
            registerSensorListeners()

            // keep screen on to not enter ambient mode
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WatchTheme {
                // render the UI
                RenderStandaloneCalib(
                    calibStateFlow = _viewModel.calibState,
                    calibTrigger = { _viewModel.calibrationTrigger() },
                    calibDone = this::onComplete
                )
            }
        }
    }

    /**
     * Register all listeners with their assigned codes.
     * Called on app startup and whenever app resumes
     */
    private fun registerSensorListeners() {
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) {
                _sensorManager.registerListener(
                    l,
                    _sensorManager.getDefaultSensor(l.code),
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            }
        }
    }

    /**
     * Unregister listeners and cancel vibration signals when exiting
     * the calibration activity
     */
    private fun onComplete() {
        if (this::_vibrator.isInitialized) _vibrator.cancel()
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) _sensorManager.unregisterListener(l)
        }
        this.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        onComplete()
    }

    override fun onPause() {
        super.onPause()
        onComplete()
    }

    override fun onResume() {
        super.onResume()
        registerSensorListeners()
    }
}
