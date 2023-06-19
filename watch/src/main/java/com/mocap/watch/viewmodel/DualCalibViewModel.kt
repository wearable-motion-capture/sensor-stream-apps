package com.mocap.watch.viewmodel

import android.app.Application
import android.hardware.SensorEvent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.sqrt

enum class DualCalibrationState {
    Idle,
    Hold,
    Forward,
    Phone,
    Error
}

class DualCalibViewModel(
    application: Application,
    vibrator: Vibrator,
    onCompleteCallback: () -> Unit
) :
    AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "DualCalibViewModel"  // for logging
        private const val CALIBRATION_WAIT = 2000L
        private const val COROUTINE_SLEEP = 10L
    }

    private val _vibrator = vibrator
    private val _onCompleteCallback = onCompleteCallback
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private var _rotVec: FloatArray = FloatArray(4) // Rotation Vector sensor or estimation
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)

    private val _calibState = MutableStateFlow(DualCalibrationState.Idle)
    val calibState = _calibState.asStateFlow()

    fun calibTrigger() {
        // update calibration state
        Log.v(TAG, "Calibration Triggered")

        _scope.launch {
            // begin with pressure reading
            _calibState.value = DualCalibrationState.Hold
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
            _calibState.value = DualCalibrationState.Forward
            start = LocalDateTime.now()
            diff = 0L
            var vibrating = false
            val quats = mutableListOf<FloatArray>()

            // collect for CALIBRATION_WAIT time
            while (diff < CALIBRATION_WAIT) {

                // the watch held horizontally if gravity in z direction is positive
                // only start considering these values if the y-rotation from
                // the "Hold" calibration position is greater than 45 deg
                val curYRot = getGlobalYRotation()
                if ((abs(holdYRot - curYRot) < 67.5f) || (_grav[2] < 9.75)) {
                    start = LocalDateTime.now()
                    quats.clear()
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
                    quats.add(_rotVec)
                    diff = Duration.between(start, LocalDateTime.now()).toMillis()
                    // and stop vibrating pulse
                    vibrating = false
                    _vibrator.cancel()
                    delay(COROUTINE_SLEEP)
                }
            }

            // Second step done. Save the average to the data singleton
            val avgQuat = quatAverage(quats)
            DataSingleton.setForwardQuat(avgQuat)

            // final vibration pulse to confirm
            _vibrator.vibrate(
                VibrationEffect.createOneShot(
                    200L, VibrationEffect.DEFAULT_AMPLITUDE
                )
            )

            // now step 3: send message to phone
            _calibState.value = DualCalibrationState.Phone
            try {
                // Connect to phone node
                val task = _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
                val res = Tasks.await(task)
                val phoneNodes = res.getValue(DataSingleton.PHONE_APP_ACTIVE).nodes
                // throw error if more than one phone connected or no phone connected
                if ((phoneNodes.count() > 1) || (phoneNodes.isEmpty())) {
                    throw Exception(
                        "0 or more than 1 node with active phone app detected. " +
                                "List of available nodes: $phoneNodes"
                    )
                }
                val node = phoneNodes.first()

                // feed into byte buffer
                val msgData = avgQuat + floatArrayOf(relPres.toFloat())
                val buffer = ByteBuffer.allocate(4 * msgData.size) // [quat, pres]
                for (v in msgData) buffer.putFloat(v)

                // send byte array in a message
                val sendMessageTask = _messageClient.sendMessage(
                    node.id, DataSingleton.CALIBRATION_PATH, buffer.array()
                )
                Tasks.await(sendMessageTask)
                Log.d(TAG, "Sent Calibration message to ${node.id}")
            } catch (exception: Exception) {
                Log.d(TAG, "Failed to send calibration request to phone:\n $exception")
                _calibState.value = DualCalibrationState.Error
            }
        }
    }


    /**
     * Checks received messages if phone calibration is complete
     */
    override fun onMessageReceived(p0: MessageEvent) {
        Log.d(TAG, "Received from: ${p0.sourceNodeId} with path ${p0.path}")
        when (p0.path) {
            DataSingleton.CALIBRATION_PATH -> {
                if (calibState.value == DualCalibrationState.Phone) {
                    _calibState.value = DualCalibrationState.Idle
                    _scope.launch {
                        // final vibration pulse to confirm
                        _vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                200L, VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                        delay(200L)
                        // complete calibration and close everything
                        _onCompleteCallback()
                    }

                }
            }
        }
    }


    /**
     * kill the calibration procedure if activity finishes unexpectedly
     */
    override fun onCleared() {
        _scope.cancel()
    }

    /**
     * returns the average of a list of quaternions
     */
    private fun quatAverage(quats: List<FloatArray>): FloatArray {
        val w = 1.0F / quats.count().toFloat()
        val q0 = quats[0] // first quaternion
        val qavg = FloatArray(q0.size) { i -> q0[i] * w }
        for (i in 1 until quats.count()) {
            val qi = quats[i]
            // dot product of qi and q0
            var dot = 0.0F
            for (j in q0.indices) dot += qi[j] * q0[j]
            if (dot < 0.0) {
                // two quaternions can represent the same orientation
                // "flip" them back close to the first quaternion if needed
                for (j in qavg.indices) qavg[j] += qi[j] * -w
            } else {
                // otherwise, just average
                for (j in qavg.indices) qavg[j] += qi[j] * w
            }
        }
        // squared sum of quat
        var sqsum = 0.0F
        for (i in qavg.indices) sqsum += qavg[i] * qavg[i]
        // l2norm
        val l2norm = sqrt(sqsum)
        // normalized, averaged quaternion
        for (i in qavg.indices) qavg[i] /= l2norm
        return qavg
    }

    /**
     * Estimates rotation around global y-axis (Up) from watch orientation.
     * This corresponds to the azimuth in polar coordinates.
     * It the angle from the z-axis (forward) in between +pi and -pi.
     */
    private fun getGlobalYRotation(): Double {
        // global smartwatch rotation as [-w,x,z,y]
        val r = floatArrayOf(-_rotVec[0], _rotVec[1], _rotVec[3], _rotVec[2])
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

    /** sensor callbacks */
    fun onPressureReadout(newReadout: SensorEvent) {
        _pres = newReadout.values
    }

    fun onGravReadout(newReadout: SensorEvent) {
        _grav = newReadout.values
    }

    fun onRotVecReadout(newReadout: SensorEvent) {
        val vals = newReadout.values
        // newReadout is [x,y,z,w, confidence]
        // our preferred order system is [w,x,y,z]
        _rotVec = floatArrayOf(vals[3], vals[0], vals[1], vals[2])
    }
}