package com.mocap.watch.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.PpgStreamState
import com.mocap.watch.SensorStreamState
import com.mocap.watch.AudioStreamState
import com.mocap.watch.service.ChannelAudioService
import com.mocap.watch.service.ChannelImuService
import com.mocap.watch.service.ChannelPpgService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.LocalDateTime

class DualViewModel(application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "DualViewModel"  // for logging
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private val _application = application

    private val _connectedNodeDisplayName = MutableStateFlow("No Device")
    val nodeName = _connectedNodeDisplayName.asStateFlow()
    var connectedNodeId: String = "none" // this will hold the connected Phone ID

    private val _pingSuccess = MutableStateFlow(false)
    val pingSuccessState = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    private val _imuStreamState = MutableStateFlow(SensorStreamState.Idle)
    val sensorStreamState = _imuStreamState.asStateFlow()

    private val _audioStreamState = MutableStateFlow(AudioStreamState.Idle)
    val soundStreamState = _audioStreamState.asStateFlow()

    private val _ppgStreamState = MutableStateFlow(PpgStreamState.Idle)
    val ppgStreamState = _ppgStreamState.asStateFlow()


    override fun onCleared() {
        super.onCleared()
        _scope.cancel()
        resetAllStreamStates()
        Log.d(TAG, "Cleared")
    }

    fun endPpg() {
        _ppgStreamState.value = PpgStreamState.Idle
        // stop an eventually running service
        Intent(_application.applicationContext, ChannelPpgService::class.java).also { intent ->
            _application.stopService(intent)
        }
    }

    fun endAudio() {
        _audioStreamState.value = AudioStreamState.Idle
        // stop an eventually running service
        Intent(_application.applicationContext, ChannelAudioService::class.java).also { intent ->
            _application.stopService(intent)
        }
    }

    fun endImu() {
        _imuStreamState.value = SensorStreamState.Idle
        Intent(_application.applicationContext, ChannelImuService::class.java).also { intent ->
            _application.stopService(intent)
        }
    }

    fun resetAllStreamStates() {
        endPpg()
        endAudio()
        endImu()
    }

    fun onServiceClose(serviceKey: String?) {
        when (serviceKey) {
            DataSingleton.PPG_CHANNEL_PATH -> endPpg()
            DataSingleton.AUDIO_CHANNEL_PATH -> endAudio()
            DataSingleton.IMU_CHANNEL_PATH -> endImu()
        }
    }

    fun onServiceUpdate(intent: Intent) {
//        when (serviceKey) {
//            DataSingleton.PPG_CHANNEL_PATH -> _viewModel.endPpg()
//            DataSingleton.SOUND_CHANNEL_PATH -> _viewModel.endSound()
//            DataSingleton.IMU_CHANNEL_PATH -> _viewModel.endSound()
//        }
    }

    fun onChannelClose(c: Channel) {
        // reset the corresponding stream loop
        when (c.path) {
            DataSingleton.PPG_CHANNEL_PATH -> endPpg()
            DataSingleton.AUDIO_CHANNEL_PATH -> endAudio()
            DataSingleton.IMU_CHANNEL_PATH -> endImu()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun audioStreamTrigger(checked: Boolean) {
        if (!checked) {
            Intent(_application.applicationContext, ChannelAudioService::class.java).also { intent ->
                _application.stopService(intent)
            }
        } else {
            val intent = Intent(_application.applicationContext, ChannelAudioService::class.java)
            intent.putExtra("sourceNodeId", connectedNodeId)
            _application.startService(intent)
            _audioStreamState.value = AudioStreamState.Streaming
        }
    }

    fun ppgStreamTrigger(checked: Boolean) {
        if (!checked) {
            Intent(_application.applicationContext, ChannelPpgService::class.java).also { intent ->
                _application.stopService(intent)
            }
        } else {
            val intent = Intent(_application.applicationContext, ChannelPpgService::class.java)
            intent.putExtra("sourceNodeId", connectedNodeId)
            _application.startService(intent)
            _ppgStreamState.value = PpgStreamState.Streaming
        }
    }

    fun imuStreamTrigger(checked: Boolean) {
        if (!checked) {
            Intent(_application.applicationContext, ChannelImuService::class.java).also { intent ->
                _application.stopService(intent)
            }
        } else {
            val intent = Intent(_application.applicationContext, ChannelImuService::class.java)
            intent.putExtra("sourceNodeId", connectedNodeId)
            _application.startService(intent)
            _imuStreamState.value = SensorStreamState.Streaming
        }
    }

    /** a simple loop that ensures both apps are active */
    fun regularConnectionCheck() {
        _scope.launch {
            while (true) {
                requestPing()
                delay(2500L)
            }
        }
    }

    /** send a ping request */
    private fun requestPing() {
//        _scope.launch {
//            _messageClient.sendMessage(connectedNodeId, DataSingleton.PING_REQ, null).await()
//            delay(1000L)
//            // reset success indicator if the response takes too long
//            if (Duration.between(_lastPing, LocalDateTime.now()).toMillis() > 1100L) {
//                _pingSuccess.value = false
//            }
//        }
    }

    /**
     * check for ping messages
     * This function is called by the listener registered in the PhoneMain Activity
     */
    fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            DataSingleton.PING_REP -> {
                _pingSuccess.value = true
                _lastPing = LocalDateTime.now()
            }

            DataSingleton.PING_REQ -> {
                // reply to a ping request
                _scope.launch {
                    _messageClient.sendMessage(connectedNodeId, DataSingleton.PING_REP, null)
                        .await()
                }
            }
        }
    }

    fun queryCapabilities() {
        _scope.launch {
            try {
                val task =
                    _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
                val res = Tasks.await(task)
                // handling happens in the callback
                for ((_, v) in res.iterator()) {
                    onCapabilityChanged(v)
                }
            } catch (exception: Exception) {
                Log.d(TAG, "Querying nodes failed: $exception")
            }
        }
    }

    fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val deviceCap = DataSingleton.PHONE_CAPABILITY
        // this checks if a phone is available at all
        when (capabilityInfo.name) {
            deviceCap -> {
                val nodes = capabilityInfo.nodes
                if (nodes.count() > 1) {
                    throw Exception("More than one node with $deviceCap detected: $nodes")
                } else if (nodes.isEmpty()) {
                    _connectedNodeDisplayName.value = "No device"
                    connectedNodeId = "none"
                } else {
                    _connectedNodeDisplayName.value = nodes.first().displayName
                    connectedNodeId = nodes.first().id
                }
                Log.d(TAG, "Connected phone : $nodes")
            }
        }
        requestPing() // check if the app is answering to pings
    }
}
