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
import java.time.Duration
import java.time.LocalDateTime
import kotlin.concurrent.thread
import com.mocap.phone.utility.quatAverage
import java.nio.ByteBuffer

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

    // Rotation Vector sensor written to by callback
    private var _rotVec: FloatArray = FloatArray(4) // [w,x,y,z]

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
            while (diff < 1000L) {
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
                    300L, VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
            Thread.sleep(300L)

            // send a reply if the calibration was triggered by a connected node with sourceID
            if (sourceId != null) {
                val buffer = ByteBuffer.allocate(4) // [mode (int)]
                buffer.putInt(0)
                val repTask =
                    _messageClient.sendMessage( // send a reply to trigger the watch streaming
                        sourceId,
                        DataSingleton.CALIBRATION_PATH,
                        buffer.array()
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
}