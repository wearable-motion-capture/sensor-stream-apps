package com.mocap.watch.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.ImuStreamState
import com.mocap.watch.service.channel.ChannelImuService
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

class SelfLabellingViewModel(application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "SelfLabellingViewModel"  // for logging
    }

    private val _application = application

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private val _connectedNodeDisplayName = MutableStateFlow("No Device")
    val nodeName = _connectedNodeDisplayName.asStateFlow()
    var connectedNodeId: String = "none" // this will hold the connected Phone ID

    private val _recordingLabel = MutableStateFlow("Unknown")
    val recLabel = _recordingLabel.asStateFlow()

    private val _pingSuccess = MutableStateFlow(false)
    val pingSuccessState = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    private val _imuStreamState = MutableStateFlow(ImuStreamState.Idle)
    val sensorStreamState = _imuStreamState.asStateFlow()


    fun endImu() {
        _imuStreamState.value = ImuStreamState.Idle
        Intent(_application.applicationContext, ChannelImuService::class.java).also { intent ->
            _application.stopService(intent)
        }
    }

    fun onServiceClose(serviceKey: String?) {
        when (serviceKey) {
            DataSingleton.IMU_PATH -> endImu()
        }
    }

    fun onChannelClose(c: ChannelClient.Channel) {
        Log.v(TAG, "channel close ${c.path}")
        // reset the corresponding stream loop
        when (c.path) {
            DataSingleton.IMU_PATH -> endImu()
        }
    }

    fun resetAllStreamStates() {
        endImu()
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

    fun onServiceUpdate(intent: Intent) {
//        when (serviceKey) {
//            DataSingleton.PPG_CHANNEL_PATH -> _viewModel.endPpg()
//            DataSingleton.SOUND_CHANNEL_PATH -> _viewModel.endSound()
//            DataSingleton.IMU_CHANNEL_PATH -> _viewModel.endSound()
//        }
    }

    /** send a ping request */
    private fun requestPing() {
        _scope.launch {
            try {
                _messageClient.sendMessage(connectedNodeId, DataSingleton.PING_REQ, null).await()
                delay(1000L)
                // reset success indicator if the response takes too long
                if (Duration.between(_lastPing, LocalDateTime.now()).toMillis() > 1100L) {
                    _pingSuccess.value = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ping message to nodeID: $connectedNodeId failed")
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
                    _messageClient.sendMessage(connectedNodeId, DataSingleton.PING_REP, null)
                        .await()
                }
            }

            // feedback from the phone that calibration is complete
            DataSingleton.CALIBRATION_PATH -> {
                val b = ByteBuffer.wrap(messageEvent.data)
                when (b.getInt(0)) {
                    3 -> { // mode == 3 means trigger IMU stream
                        if (_imuStreamState.value == ImuStreamState.Idle) {
                            val intent = Intent(
                                _application.applicationContext,
                                ChannelImuService::class.java
                            )
                            intent.putExtra("sourceNodeId", connectedNodeId)
                            _application.startService(intent)
                            _imuStreamState.value = ImuStreamState.Streaming
                        } else {
                            endImu()
                        }
                    }
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

    /**
     * kill the calibration procedure if activity finishes unexpectedly
     */
    override fun onCleared() {
        super.onCleared()
        _scope.cancel()
        resetAllStreamStates()
        Log.d(TAG, "Cleared")
    }
}