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
import com.mocap.watch.utility.getGlobalYRotation
import com.mocap.watch.utility.quatAverage
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
import kotlin.math.sin

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
        private const val CALIBRATION_WAIT = 1000L
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
        Log.v(TAG, "Calibration Triggered")

        _scope.launch {
            // update calibration state
            _calibState.value = DualCalibrationState.Hold
            var start = LocalDateTime.now()
            var diff = 0L

            // begin with step 1:
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
            _calibState.value = DualCalibrationState.Forward
            start = LocalDateTime.now()
            diff = 0L
            var vibrating = false
            val quats = mutableListOf<FloatArray>()

            // collect for CALIBRATION_WAIT time
            while (diff < CALIBRATION_WAIT) {

                // the watch is held horizontally, if gravity in z direction is close to std gravity
                // only start considering these values, if the y-rotation from
                // the "Hold" calibration position is greater than ~80 deg
                val curYRot = getGlobalYRotation(_rotVec)
                if ((abs(sin(Math.toRadians(holdYRot)) - sin(Math.toRadians(curYRot))) < 0.4)
                    || (_grav[2] < 9.75)
                ) {
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
                    if (vibrating) {
                        vibrating = false
                        _vibrator.cancel()
                    }
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
                val buffer = ByteBuffer.allocate(4 * 6) // [quat (4), pres, mode]
                for (v in avgQuat) buffer.putFloat(v)
                buffer.putFloat(relPres.toFloat())
                buffer.putInt(1) // mode 1: no automatic calibration reply send manual message later

                // Send byte array in a message
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

    /** sensor callbacks */
    fun onPressureReadout(newReadout: SensorEvent) {
        _pres = newReadout.values
    }

    fun onGravReadout(newReadout: SensorEvent) {
        _grav = newReadout.values
    }

    fun onRotVecReadout(newReadout: SensorEvent) {
        // newReadout is [x,y,z,w, confidence]
        // our preferred order system is [w,x,y,z]
        _rotVec = floatArrayOf(
            newReadout.values[3],
            newReadout.values[0],
            newReadout.values[1],
            newReadout.values[2]
        )
    }
}