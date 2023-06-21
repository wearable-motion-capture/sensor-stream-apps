package com.mocap.watch.activity

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.modules.SensorListener
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderDualCalib
import com.mocap.watch.viewmodel.DualCalibViewModel


class DualCalibActivity : ComponentActivity() {

    companion object {
        private const val TAG = "DualCalibration"  // for logging
    }

    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private lateinit var _viewModel: DualCalibViewModel
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

            // with the vibration service, create the view model
            _viewModel = DualCalibViewModel(
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
                RenderDualCalib(
                    calibStateFlow = _viewModel.calibState,
                    calibTrigger = { _viewModel.calibTrigger() },
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
        if (this::_viewModel.isInitialized) _messageClient.addListener(_viewModel)
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
        if (this::_viewModel.isInitialized) _messageClient.removeListener(_viewModel)
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
