package com.mocap.phone.viewmodel

import android.app.Application
import android.hardware.SensorEvent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDateTime
import kotlin.concurrent.thread
import kotlin.math.sqrt

class CalibViewModel(application: Application, vibrator: Vibrator) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "PhoneCalibration"  // for logging
    }

    private val _vibrator = vibrator
    private val _messageClient by lazy { Wearable.getMessageClient(application) }

    private val _quatReading = MutableStateFlow(
        floatArrayOf(1.0F, 0.0F, 0.0F, 0.0F) // identity quat
    )
    val quatReading = _quatReading.asStateFlow()

    // Rotation Vector sensor written to by callback
    private var _rotVec: FloatArray = FloatArray(4) // [w,x,y,z]

    /**
     * records orientation for CALIBRATION_WAIT milliseconds and saves average to DataSingleton
     */
    fun freeHipsCalibrationTrigger(doneCallback: () -> Unit, sourceId: String? = null) {
        Log.v(TAG, "Calibration Triggered")
        thread {
            val start = LocalDateTime.now()
            var diff = 0L
            val quats = mutableListOf<FloatArray>()

            // collect for CALIBRATION_WAIT time
            while (diff < 100L) {
                quats.add(_rotVec)
                diff = Duration.between(start, LocalDateTime.now()).toMillis()
                _quatReading.value = _rotVec
                Thread.sleep(10L)
            }

            // Save the average to the data singleton
            val avgQuat = quatAverage(quats)
            DataSingleton.setPhoneForwardQuat(avgQuat)

            // send a reply if the calibration was triggered by a connected node with sourceID
            if (sourceId != null) {
                val repTask = _messageClient.sendMessage(
                    sourceId, DataSingleton.CALIBRATION_PATH, null
                )
                Tasks.await(repTask)
            }

            // finish activity
            doneCallback()
        }
        Log.v(TAG, "Calibration Done")
    }

    /**
     * records orientation for CALIBRATION_WAIT milliseconds and saves average to DataSingleton
     */
    fun upperArmCalibrationTrigger(doneCallback: () -> Unit, sourceId: String? = null) {

        Log.v(TAG, "Calibration Triggered")
        thread {
            val start = LocalDateTime.now()
            var diff = 0L
            val quats = mutableListOf<FloatArray>()

            // collect for CALIBRATION_WAIT time
            while (diff < 2000L) {
                quats.add(_rotVec)
                diff = Duration.between(start, LocalDateTime.now()).toMillis()
                _quatReading.value = _rotVec
                Thread.sleep(10L)
            }

            // Save the average to the data singleton
            val avgQuat = quatAverage(quats)
            DataSingleton.setPhoneForwardQuat(avgQuat)

            // signal completion with vibration
            _vibrator.vibrate(
                VibrationEffect.createOneShot(
                    500L, VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
            Thread.sleep(500L)

            // send a reply if the calibration was triggered by a connected node with sourceID
            if (sourceId != null) {
                val repTask = _messageClient.sendMessage(
                    sourceId, DataSingleton.CALIBRATION_PATH, null
                )
                Tasks.await(repTask)
            }

            // finish activity
            doneCallback()
        }
        Log.v(TAG, "Calibration Done")
    }

    /**
     * sensor callback
     */
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
}