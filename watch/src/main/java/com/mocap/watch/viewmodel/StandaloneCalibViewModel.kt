package com.mocap.watch.viewmodel

import android.app.Application
import android.hardware.SensorEvent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.mocap.watch.DataSingleton
import com.mocap.watch.utility.getGlobalYRotation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

enum class CalibrationState {
    Idle,
    Hold,
    Forward
}


class StandaloneCalibViewModel(
    application: Application,
    vibrator: Vibrator,
    onCompleteCallback: () -> Unit
) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "StandaloneCalibViewModel"  // for logging
        private const val CALIBRATION_WAIT = 3000 // wait time in one calibration position
        private const val COROUTINE_SLEEP = 10L
    }

    private val _vibrator = vibrator
    private val _onCompleteCallback = onCompleteCallback
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private var _rotVec: FloatArray = FloatArray(4) // Rotation Vector sensor or estimation
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)

    private val _calibState = MutableStateFlow(CalibrationState.Idle)
    val calibState = _calibState.asStateFlow()

    fun calibrationTrigger() {
        // update calibration state
        Log.v(TAG, "Calibration Triggered")


        _scope.launch {
            // begin with pressure reading
            _calibState.value = CalibrationState.Hold
            var start = LocalDateTime.now()
            var diff = 0L

            // collect pressure and y angle for CALIBRATION_WAIT time
            val pressures = mutableListOf(_pres[0])
            val holdDegrees = mutableListOf(getGlobalYRotation(_rotVec))
            while (diff < CALIBRATION_WAIT) {
                pressures.add(_pres[0])
                holdDegrees.add(getGlobalYRotation(_rotVec))
                diff = Duration.between(start, LocalDateTime.now()).toMillis()
            }

            // first calibration step done. Save the averages
            val relPres = pressures.average()
            val holdYRot = holdDegrees.average()
            DataSingleton.setCalibPress(relPres.toFloat())

            // next step: forward orientation reading
            // reset start and diff
            _calibState.value = CalibrationState.Forward
            start = LocalDateTime.now()
            diff = 0L
            var vibrating = false
            val northDegrees = mutableListOf<Double>()

            // collect for CALIBRATION_WAIT time
            while (diff < CALIBRATION_WAIT) {

                // the watch held horizontally if gravity in z direction is positive
                // only start considering these values if the y-rotation from
                // the "Hold" calibration position is greater than 45 deg
                val curYRot = getGlobalYRotation(_rotVec)
                if ((abs(holdYRot - curYRot) < 67.5f) || (_grav[2] < 9.75)) {
                    start = LocalDateTime.now()
                    northDegrees.clear()
                    if (!vibrating) {
                        vibrating = true
                        _vibrator.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 200L, 100L, 200L, 100L), 1
                            )
                        )
                    }
                } else {
                    // if all is good, store to list of vales
                    northDegrees.add(curYRot)
                    diff = Duration.between(start, LocalDateTime.now()).toMillis()
                    // and stop vibrating pulse
                    vibrating = false
                    _vibrator.cancel()
                    delay(COROUTINE_SLEEP)
                }
            }

            // Second step done. Save the average to the data singleton
            // add 90 degrees for forward orientation of arm and hip
            DataSingleton.setCalibNorth(northDegrees.average() + 90)

            // final vibration pulse to confirm
            _vibrator.vibrate(
                VibrationEffect.createOneShot(
                    200L, VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
            delay(200L)
            // complete calibration and close everything
            _calibState.value = CalibrationState.Idle
            _onCompleteCallback()
        }
    }

    /**
     * kill the calibration procedure if activity finishes unexpectedly
     */
    override fun onCleared() {
        _scope.cancel()
    }

    /** sensor callback */
    fun onPressureReadout(newReadout: SensorEvent) {
        _pres = newReadout.values
    }

    /** sensor callback */
    fun onGravReadout(newReadout: SensorEvent) {
        _grav = newReadout.values
    }

    /** sensor callback */
    fun onRotVecReadout(newReadout: SensorEvent) {
        _rotVec = floatArrayOf(
            newReadout.values[3],
            newReadout.values[0],
            newReadout.values[1],
            newReadout.values[2]
        )
    }
}