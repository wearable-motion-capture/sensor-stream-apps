package com.mocap.watch.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.hardware.SensorEvent
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.AudioStreamState
import com.mocap.watch.DataSingleton
import com.mocap.watch.ImuStreamState
import com.mocap.watch.service.channel.ChannelAudioService
import com.mocap.watch.service.channel.ChannelImuService
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

class PhonePocketViewModel(application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "FreeHipsViewModel"  // for logging
        private const val COROUTINE_SLEEP = 10L
        private const val CALIBRATION_WAIT = 100L

    }

    private val _application = application

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private var _rotVec: FloatArray = FloatArray(4) // Rotation Vector sensor or estimation
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)

    private val _gravDiff = MutableStateFlow(0.0f)
    val gravDiff = _gravDiff.asStateFlow()

    private val _connectedNodeDisplayName = MutableStateFlow("No Device")
    val nodeName = _connectedNodeDisplayName.asStateFlow()
    var connectedNodeId: String = "none" // this will hold the connected Phone ID

    private val _pingSuccess = MutableStateFlow(false)
    val pingSuccessState = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    private val _calSuccess = MutableStateFlow(false)
    val calSuccessState = _calSuccess.asStateFlow()

    private val _imuStreamState = MutableStateFlow(ImuStreamState.Idle)
    val sensorStreamState = _imuStreamState.asStateFlow()

    private val _audioStreamState = MutableStateFlow(AudioStreamState.Idle)
    val audioStreamState = _audioStreamState.asStateFlow()

    fun endAudio() {
        _audioStreamState.value = AudioStreamState.Idle
        // stop an eventually running service
        Intent(_application.applicationContext, ChannelAudioService::class.java).also { intent ->
            _application.stopService(intent)
        }
    }

    fun endImu() {
        _imuStreamState.value = ImuStreamState.Idle
        Intent(_application.applicationContext, ChannelImuService::class.java).also { intent ->
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

    fun onChannelClose(c: ChannelClient.Channel) {
        // reset the corresponding stream loop
        when (c.path) {
            DataSingleton.AUDIO_PATH -> endAudio()
            DataSingleton.IMU_PATH -> endImu()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun audioStreamTrigger(checked: Boolean) {
        if (!checked) {
            Intent(
                _application.applicationContext,
                ChannelAudioService::class.java
            ).also { intent ->
                _application.stopService(intent)
            }
        } else {
            val intent = Intent(_application.applicationContext, ChannelAudioService::class.java)
            intent.putExtra("sourceNodeId", connectedNodeId)
            _application.startService(intent)
            _audioStreamState.value = AudioStreamState.Streaming
        }
    }

    fun imuStreamTrigger(checked: Boolean) {
        if (!checked) {
            Intent(_application.applicationContext, ChannelImuService::class.java).also { intent ->
                _application.stopService(intent)
            }
        } else {
            calibPhoneAndStartIMUStream(
                DataSingleton.forwardQuat.value,
                DataSingleton.calib_pres.value
            )
        }
    }

    fun gravityCheck() {
        _scope.launch {
            val gravThresh = 9.75f
            while (_imuStreamState.value != ImuStreamState.Streaming) {

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

    private fun calibPhoneAndStartIMUStream(cal_fwd: FloatArray, cal_pres: Float) {
        _scope.launch {
            try {
                // feed into byte buffer
                val msgData = cal_fwd + floatArrayOf(
                    cal_pres,
                    1f // mode. 1f means free hips
                )
                val buffer = ByteBuffer.allocate(4 * msgData.size) // [quat, pres]
                for (v in msgData) buffer.putFloat(v)

                // send byte array in a message
                val sendMessageTask = _messageClient.sendMessage(
                    connectedNodeId, DataSingleton.CALIBRATION_PATH, buffer.array()
                )
                Tasks.await(sendMessageTask)
                Log.d(TAG, "Sent Calibration message to $connectedNodeId")
            } catch (e: Exception) {
                Log.w(TAG, "Calibration message failed for $connectedNodeId")
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
                val intent = Intent(_application.applicationContext, ChannelImuService::class.java)
                intent.putExtra("sourceNodeId", connectedNodeId)
                _application.startService(intent)
                _imuStreamState.value = ImuStreamState.Streaming
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