package com.mocap.watch.activity

import android.hardware.Sensor
import android.os.*
import android.view.WindowManager
import androidx.activity.compose.setContent
import com.mocap.watch.modules.CalibStateModule
import com.mocap.watch.modules.SensorListener
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderStandaloneCalib


class StandaloneCalibActivity : SensorActivity() {

    companion object {
        private const val TAG = "Calibration"  // for logging
    }

    private lateinit var _vibrator: Vibrator
    private lateinit var _calibStateModule: CalibStateModule

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WatchTheme {

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
                _calibStateModule = CalibStateModule(
                    vibrator = _vibrator,
                    calibDone = { done() }
                )

                // add Sensor Listeners with our calibrator callbacks
                // this makes use of the SensorActivity class
                val listeners = listOf(
                    SensorListener(
                        Sensor.TYPE_PRESSURE
                    ) { _calibStateModule.onPressureReadout(it) },
                    SensorListener(
                        Sensor.TYPE_ROTATION_VECTOR
                    ) { _calibStateModule.onRotVecReadout(it) },
                    SensorListener(
                        Sensor.TYPE_GRAVITY
                    ) { _calibStateModule.onGravReadout(it) }
                )
                super.setSensorListeners(listeners) // finally register them with the manager

                // keep screen on to not enter ambient mode
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // render the UI
                RenderStandaloneCalib(
                    calibStateFlow = _calibStateModule.calibState,
                    calibTrigger = { _calibStateModule.calibrationTrigger() },
                    calibDone = { done() }
                )
            }
        }
    }

    private fun cancelVibration() {
        if (this::_vibrator.isInitialized) {
            _vibrator.cancel()
        }
    }

    /** a callback to exit the calibration activity */
    private fun done() {
        cancelVibration()
        this.finish()
    }

    override fun onPause() {
        super.onPause()
        cancelVibration()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelVibration()
    }
}
