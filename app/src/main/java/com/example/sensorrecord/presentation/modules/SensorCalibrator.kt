package com.example.sensorrecord.presentation.modules

import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDateTime
import kotlin.concurrent.thread
import kotlin.math.abs

class SensorCalibrator(globalState: GlobalState) {

    companion object {
        private const val TAG = "Calibrator"  // for logging
        private const val CALIBRATION_WAIT = 3000 // wait time in one calibration position
    }

    private val _gs = globalState

    // the initial pressure for relative pressure estimations
    private val _initPressure = MutableStateFlow(0.0)
    val initPres = _initPressure.asStateFlow()

    // magnetic north pole direction in relation to body orientation
    private val _forwardNorthDegree = MutableStateFlow(0.0)
    val northDeg = _forwardNorthDegree.asStateFlow()

    /**
     * Triggered by the calibration button. It goes through all 4 calibration stages
     * to set required normalization parameters
     */
    fun calibrationTrigger(vibrator: Vibrator) {

        // verify that app is in a state that allows to start the calibration
        if (_gs.getCalibState() != CalibrationState.Idle) {
            Log.v(TAG, "Calibration already in progress")
            return
        }

        fun forwardStep(holdYRot: Float) {
            // Get body orientation from when the participant extends the watch-arm forward
            // perpendicular to the hip
            _gs.setCalibState(CalibrationState.Forward)
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
                    if ((abs(holdYRot - curYRot) < 67.5f) || (_gs.getGravity()[2] < 9.75)) {
                        startTime = LocalDateTime.now()
                        northDegrees.clear()
                        if (!vibrating) {
                            vibrating = true
                            vibrator.vibrate(
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
                        vibrator.cancel()
                    }
                }

                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        500L, VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )

                // last calibration step done
                _forwardNorthDegree.value =
                    northDegrees.average() + 90 // add 90 degrees for forward orientation of arm and hip

                // set app state to ready to begin recording
                _gs.setCalibState(CalibrationState.Idle)
                // return to overview
                _gs.setView(Views.Home)
            }
        }

        fun holdStep() {
            // begin with atmospheric pressure in initial hold position after pressing "start"
            _gs.setCalibState(CalibrationState.Hold)
            thread {
                val start = LocalDateTime.now()
                var diff = 0L

                val pressures = mutableListOf(_gs.getPressure()[0])
                while (diff < CALIBRATION_WAIT) {
                    pressures.add(_gs.getPressure()[0])
                    diff = Duration.between(start, LocalDateTime.now()).toMillis()
                }
                // first calibration step done
                _initPressure.value = pressures.average()
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        500L, VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
                // continue with second step and pass current y-rotation
                forwardStep(holdYRot = getGlobalYRotation())
            }
        }

        // this is the start of the calibration that calls all above functions iteratively
        holdStep()
    }

    /**
     * Estimates rotation around global y-axis (Up) from watch orientation.
     * This corresponds to the azimuth in polar coordinates. It the angle from the z-axis (forward)
     * in between +pi and -pi.
     */
    private fun getGlobalYRotation(): Float {
        val rotVec = _gs.getRotVec()
        // smartwatch rotation to [-w,x,z,y]
        val r = floatArrayOf(-rotVec[3], rotVec[0], rotVec[2], rotVec[1])
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

        return yRot * 57.29578f
    }
}