package com.mocap.watch.stateModules

import android.os.VibrationEffect
import android.os.Vibrator
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDateTime
import kotlin.concurrent.thread
import kotlin.math.abs

enum class CalibrationState {
    Idle,
    Hold,
    Forward
}


class CalibStateModule(vibrator: Vibrator, calibDone: () -> Unit) {

    companion object {
        private const val TAG = "CalibrationActivity"  // for logging
        private const val CALIBRATION_WAIT = 3000 // wait time in one calibration position
    }

    private val _calibDone = calibDone // callback to close the activity after calibration is done
    private val _vibrator = vibrator

    private val _calibState = MutableStateFlow(CalibrationState.Idle)
    val calibState = _calibState.asStateFlow()


    private var _holdYRot = 0.0 // intermediate rotation between hold and forward calibration step
    private var _rotVec: FloatArray = FloatArray(5) // Rotation Vector sensor or estimation
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)


    fun reset() {
        _calibState.value = CalibrationState.Idle
    }

    /**
     * Triggered by the calibration button. It goes through all 4 calibration stages
     * to set required normalization parameters
     */
    fun calibrationTrigger() {

        // verify that app is in a state that allows to start the calibration
        if (calibState.value != CalibrationState.Idle) {
            return
        }

        // the hold-step is the start of the calibration procedure
        holdStep()
    }

    /**
     *  begin with atmospheric pressure in initial hold position after pressing "start"
     */
    private fun holdStep() {
        // update calibration state
        _calibState.value = CalibrationState.Hold
        thread {
            val start = LocalDateTime.now()
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
            DataSingleton.setCalibPress(pressures.average())
            _holdYRot = holdDegrees.average()

            // signal with vibration
            _vibrator.vibrate(
                VibrationEffect.createOneShot(
                    500L, VibrationEffect.DEFAULT_AMPLITUDE
                )
            )

            // continue with second step and pass current y-rotation
            forwardStep()
        }
    }

    /**
     * Get body orientation from when the participant extends
     * the watch-arm forward perpendicular to the hip
     */
    private fun forwardStep() {
        // update calibration state
        _calibState.value = CalibrationState.Forward
        thread {
            var startTime = LocalDateTime.now()
            var diff = 0L
            var vibrating = false
            val northDegrees = mutableListOf(getGlobalYRotation())

            while (diff < CALIBRATION_WAIT) {

                // the watch held horizontally if gravity in z direction is positive
                // only start considering these values if the y-rotation from
                // the "Hold" calibration position is greater than 45 deg
                val curYRot = getGlobalYRotation()
                if ((abs(_holdYRot - curYRot) < 67.5f) || (_grav[2] < 9.75)) {
                    startTime = LocalDateTime.now()
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
                    diff = Duration.between(startTime, LocalDateTime.now()).toMillis()
                    vibrating = false
                    _vibrator.cancel()
                }
            }
            // final vibration pulse to confirm
            _vibrator.vibrate(
                VibrationEffect.createOneShot(
                    500L, VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
            // add 90 degrees for forward orientation of arm and hip
            DataSingleton.setCalibNorth(northDegrees.average() + 90)

            // last calibration step completed
            _calibState.value = CalibrationState.Idle
            _calibDone()
        }
    }

    /** sensor callback */
    fun onPressureReadout(newReadout: FloatArray) {
        _pres = newReadout
    }

    /** sensor callback */
    fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }

    /** sensor callback */
    fun onRotVecReadout(newReadout: FloatArray) {
        _rotVec = newReadout // [x,y,z,w]
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