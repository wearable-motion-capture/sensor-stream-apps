package com.mocap.watch.modules

import android.app.Application
import android.hardware.SensorEvent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.mocap.watch.DataSingleton
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

    private val _calibState = MutableStateFlow(CalibrationState.Idle)
    val calibState = _calibState.asStateFlow()

    private var _rotVec: FloatArray = FloatArray(5) // Rotation Vector sensor or estimation
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)


    /**
     * Triggered by the calibration button. It goes through all 4 calibration stages
     * to set required normalization parameters
     */
    fun calibrationTrigger() {
        // update calibration state
        Log.v(TAG, "Calibration Triggered")


        // verify that app is in a state that allows to start the calibration
        if (calibState.value != CalibrationState.Idle) {
            return
        }

        _scope.launch {
            // begin with pressure reading
            _calibState.value = CalibrationState.Hold
            var start = LocalDateTime.now()
            var diff = 0L

            // collect pressure and y angle for CALIBRATION_WAIT time
            val pressures = mutableListOf(_pres[0])
            val holdDegrees = mutableListOf(getGlobalYRotation())
            while (diff < CALIBRATION_WAIT) {
                pressures.add(_pres[0])
                holdDegrees.add(getGlobalYRotation())
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
                val curYRot = getGlobalYRotation()
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
        _rotVec = newReadout.values // [x,y,z,w]
    }

    /**
     * Estimates rotation around global y-axis (Up) from watch orientation.
     * This corresponds to the azimuth in polar coordinates. It the angle from the z-axis (forward)
     * in between +pi and -pi.
     */
    private fun getGlobalYRotation(): Double {
        // smartwatch rotation to [-w,x,z,y]
        val r = floatArrayOf(-_rotVec[3], _rotVec[0], _rotVec[2], _rotVec[1])
        val p = floatArrayOf(0f, 0f, 0f, 1f) // forward vector with [0,x,y,z]

        // this is the result of H(R,P)
        val hrp = floatArrayOf(
            r[0] * p[0] - r[1] * p[1] - r[2] * p[2] - r[3] * p[3],
            r[0] * p[1] + r[1] * p[0] + r[2] * p[3] - r[3] * p[2],
            r[0] * p[2] - r[1] * p[3] + r[2] * p[0] + r[3] * p[1],
            r[0] * p[3] + r[1] * p[2] - r[2] * p[1] + r[3] * p[0]
        )

        val r_p = floatArrayOf(r[0], -r[1], -r[2], -r[3]) // this ir R'
        // the final H(H(R,P),R')
        val p_p = floatArrayOf(
            hrp[0] * r_p[0] - hrp[1] * r_p[1] - hrp[2] * r_p[2] - hrp[3] * r_p[3],
            hrp[0] * r_p[1] + hrp[1] * r_p[0] + hrp[2] * r_p[3] - hrp[3] * r_p[2],
            hrp[0] * r_p[2] - hrp[1] * r_p[3] + hrp[2] * r_p[0] + hrp[3] * r_p[1],
            hrp[0] * r_p[3] + hrp[1] * r_p[2] - hrp[2] * r_p[1] + hrp[3] * r_p[0]
        )
        // get angle with atan2
        val yRot = kotlin.math.atan2(p_p[1], p_p[3])

        return yRot * 57.29578
    }
}