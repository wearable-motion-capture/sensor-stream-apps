package com.mocap.phone.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
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

class PhoneViewModel(application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "PhoneViewModel"
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    // UI elements to inform the user about the internal state
    private var _connectedNodeId: String = "none"
    private val _connectedNodeDisplayName = MutableStateFlow("none")
    val nodeName = _connectedNodeDisplayName.asStateFlow()
    private val _pingSuccess = MutableStateFlow(false)
    val appActive = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    private val _audioStreamState = MutableStateFlow(false)
    val audioStreamState = _audioStreamState.asStateFlow()

    private val _ppgStreamState = MutableStateFlow(false)
    val ppgStreamState = _ppgStreamState.asStateFlow()

    private val _imuStreamState = MutableStateFlow(false)
    val imuStreamState = _imuStreamState.asStateFlow()

    private val _audioBroadcastHz = MutableStateFlow(0.0F)
    val audioBroadcastHz = _audioBroadcastHz.asStateFlow()

    private val _audioStreamQueue = MutableStateFlow(0)
    val audioStreamQueue = _audioStreamQueue.asStateFlow()

    private val _imuInHz = MutableStateFlow(0.0F)
    val imuInHz = _imuInHz.asStateFlow()

    private val _imuOutHz = MutableStateFlow(0.0F)
    val imuOutHz = _imuOutHz.asStateFlow()

    private val _imuQueueSize = MutableStateFlow(0)
    val imuQueueSize = _imuQueueSize.asStateFlow()

    private val _ppgInHz = MutableStateFlow(0.0F)
    val ppgInHz = _ppgInHz.asStateFlow()

    private val _ppgOutHz = MutableStateFlow(0.0F)
    val ppgOutHz = _ppgOutHz.asStateFlow()

    private val _ppgQueueSize = MutableStateFlow(0)
    val ppgQueueSize = _ppgQueueSize.asStateFlow()

    fun onServiceUpdate(intent: Intent) {
        when (intent.getStringExtra(DataSingleton.BROADCAST_SERVICE_KEY)) {
            DataSingleton.IMU_PATH -> {
                _imuStreamState.value = intent.getBooleanExtra(
                    DataSingleton.BROADCAST_SERVICE_STATE, false
                )
                _imuInHz.value = intent.getFloatExtra(
                    DataSingleton.BROADCAST_SERVICE_HZ_IN, 0.0F
                )
                _imuOutHz.value = intent.getFloatExtra(
                    DataSingleton.BROADCAST_SERVICE_HZ_OUT, 0.0F
                )
                _imuQueueSize.value = intent.getIntExtra(
                    DataSingleton.BROADCAST_SERVICE_QUEUE, 0
                )
            }

            DataSingleton.AUDIO_PATH -> {
                _audioStreamState.value = intent.getBooleanExtra(
                    DataSingleton.BROADCAST_SERVICE_STATE, false
                )
                _audioBroadcastHz.value = intent.getFloatExtra(
                    DataSingleton.BROADCAST_SERVICE_HZ_IN, 0.0F
                )
                _audioStreamQueue.value = intent.getIntExtra(
                    DataSingleton.BROADCAST_SERVICE_QUEUE, 0
                )
            }

            DataSingleton.PPG_PATH -> {
                _ppgStreamState.value = intent.getBooleanExtra(
                    DataSingleton.BROADCAST_SERVICE_STATE, false
                )
                _ppgInHz.value = intent.getFloatExtra(
                    DataSingleton.BROADCAST_SERVICE_HZ_IN, 0.0F
                )
                _ppgOutHz.value = intent.getFloatExtra(
                    DataSingleton.BROADCAST_SERVICE_HZ_OUT, 0.0F
                )
                _ppgQueueSize.value = intent.getIntExtra(
                    DataSingleton.BROADCAST_SERVICE_QUEUE, 0
                )
            }
        }
    }


    /**
     * Switch between Left-Hand Mode and Right-Hand Mode
     */
    fun switchHand() {
        // defaults
        var hand = "left"
        var port = DataSingleton.IMU_PORT_LEFT

        // change in case current value is defaults
        if (DataSingleton.imuPort.value == DataSingleton.IMU_PORT_LEFT) {
            hand = "right"
            port = DataSingleton.IMU_PORT_RIGHT
        }

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.getApplication())
        with(sharedPref.edit()) {
            putInt(DataSingleton.PORT_KEY, port)
            apply()
        }
        DataSingleton.setImuPort(port)
        Log.d(TAG, "switched to $hand-hand mode. Set target port to $port")
    }

    fun regularUiUpdates() {
        _scope.launch {
            while (true) {
                requestPing() // confirm both apps are active
                delay(2000L)
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
        val deviceCap = DataSingleton.WATCH_CAPABILITY
        // this checks if a phone is available at all
        when (capabilityInfo.name) {
            deviceCap -> {
                val nodes = capabilityInfo.nodes
                if (nodes.count() > 1) {
                    throw Exception("More than one node with $deviceCap detected: $nodes")
                } else if (nodes.isEmpty()) {
                    _connectedNodeDisplayName.value = "No device"
                    _connectedNodeId = "none"
                } else {
                    _connectedNodeDisplayName.value = nodes.first().displayName
                    _connectedNodeId = nodes.first().id
                }
                Log.d(TAG, "Connected phone : $nodes")
            }
        }
        requestPing() // check if the app is answering to pings
    }

    /** send a ping request */
    private fun requestPing() {
        _scope.launch {
            try {
                _messageClient.sendMessage(_connectedNodeId, DataSingleton.PING_REQ, null).await()
                delay(1000L)
                // reset success indicator if the response takes too long
                if (Duration.between(_lastPing, LocalDateTime.now()).toMillis() > 1100L) {
                    _pingSuccess.value = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ping message to nodeID: $_connectedNodeId failed")
            }
        }
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
                    _messageClient.sendMessage(_connectedNodeId, DataSingleton.PING_REP, null)
                        .await()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _scope.cancel()
        Log.d(TAG, "Cleared")
    }
}