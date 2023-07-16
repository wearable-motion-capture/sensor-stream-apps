package com.mocap.watch.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import com.mocap.watch.ImuStreamState
import com.mocap.watch.AudioStreamState
import com.mocap.watch.DataSingleton
import com.mocap.watch.service.udp.UdpAudioService
import com.mocap.watch.service.udp.UdpImuService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class StandaloneViewModel(application: Application) :
    AndroidViewModel(application) {

    /** setup-specific parameters */
    companion object {
        private const val TAG = "StandaloneModule"  // for logging
    }

    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private val _application = application

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
}
