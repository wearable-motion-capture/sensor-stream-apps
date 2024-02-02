package com.mocap.watch.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.hardware.SensorEvent
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import com.mocap.watch.ImuStreamState
import com.mocap.watch.AudioStreamState
import com.mocap.watch.DataSingleton
import com.mocap.watch.service.udp.UdpAudioService
import com.mocap.watch.service.udp.UdpImuService
import com.mocap.watch.utility.quatAverage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime


class WatchOnlyViewModel(application: Application) :
    AndroidViewModel(application) {

    /** setup-specific parameters */
    companion object {
        private const val TAG = "StandaloneModule"  // for logging
        private const val COROUTINE_SLEEP = 10L
        private const val CALIBRATION_WAIT = 100L
    }

    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private val _application = application

    private var _rotVec: FloatArray = FloatArray(4) // Rotation Vector sensor or estimation
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)

    private val _gravDiff = MutableStateFlow(0.0f)
    val gravDiff = _gravDiff.asStateFlow()

    private val _calSuccess = MutableStateFlow(false)
    val calSuccessState = _calSuccess.asStateFlow()

    private val _imuStrState = MutableStateFlow(ImuStreamState.Idle)
    val sensorStrState = _imuStrState.asStateFlow()

    private val _audioStrState = MutableStateFlow(AudioStreamState.Idle)
    val audioStrState = _audioStrState.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        _scope.cancel()
        resetAllStreamStates()
        Log.d(TAG, "Cleared")
    }

    fun endAudio() {
        Log.v(TAG, "end audio called")
        _audioStrState.value = AudioStreamState.Idle
        // stop an eventually running service
        Intent(_application.applicationContext, UdpAudioService::class.java).also { intent ->
            _application.stopService(intent)
        }
    }

    fun endImu() {
        _imuStrState.value = ImuStreamState.Idle
        Intent(_application.applicationContext, UdpImuService::class.java).also { intent ->
            _application.stopService(intent)
        }
        // restart gravity check for posture
        gravityCheck()
    }

    fun resetAllStreamStates() {
        endAudio()
        endImu()
    }

    fun onServiceClose(serviceKey: String?) {
        when (serviceKey) {
            DataSingleton.AUDIO_PATH -> endAudio()
            DataSingleton.IMU_PATH -> endImu()
        }
    }

    fun onServiceUpdate(intent: Intent) {
//        when (serviceKey) {
//            DataSingleton.PPG_CHANNEL_PATH -> _viewModel.endPpg()
//            DataSingleton.SOUND_CHANNEL_PATH -> _viewModel.endSound()
//            DataSingleton.IMU_CHANNEL_PATH -> _viewModel.endSound()
//        }
    }

    fun imuStreamTrigger(checked: Boolean) {
        if (!checked) {
            Intent(_application.applicationContext, UdpImuService::class.java).also { intent ->
                _application.stopService(intent)
            }
        } else {
            val intent = Intent(_application.applicationContext, UdpImuService::class.java)
            _application.startService(intent)
            _imuStrState.value = ImuStreamState.Streaming
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun audioStreamTrigger(checked: Boolean) {
        if (!checked) {
            Intent(
                _application.applicationContext,
                UdpAudioService::class.java
            ).also { intent ->
                _application.stopService(intent)
            }
        } else {
            val intent = Intent(_application.applicationContext, UdpAudioService::class.java)
            _application.startService(intent)
            _audioStrState.value = AudioStreamState.Streaming
        }
    }

    fun gravityCheck() {
        _scope.launch {
            val gravThresh = 9.75f
            while (_imuStrState.value != ImuStreamState.Streaming) {

                var start = LocalDateTime.now()
                var diff = 0L
                val quats = mutableListOf<FloatArray>()
                val pressures = mutableListOf(_pres[0])

                // collect for CALIBRATION_WAIT time
                while (diff < CALIBRATION_WAIT) {
                    _gravDiff.value = (_grav[2] / 9.81f + 1f) * 0.5f
                    if (_grav[2] < gravThresh) {
                        start = LocalDateTime.now()
                        quats.clear()
                        pressures.clear()
                        _calSuccess.value = false
                    } else {
                        // if all is good, store to list of vales
                        quats.add(_rotVec)
                        pressures.add(_pres[0])
                        diff = Duration.between(start, LocalDateTime.now()).toMillis()
                        delay(COROUTINE_SLEEP)
                    }
                }
                // We got an estimate save the average to the data singleton
                val avgQuat = quatAverage(quats)
                DataSingleton.setForwardQuat(avgQuat)
                val relPres = pressures.average().toFloat()
                DataSingleton.setCalibPress(relPres)
                _calSuccess.value = true
            }
        }
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
