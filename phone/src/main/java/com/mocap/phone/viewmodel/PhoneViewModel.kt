package com.mocap.phone.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
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
import java.nio.ByteBuffer
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

    // UI elements to inform the user about the connection to the watch
    private var _connectedNodeId: String = "none"
    private val _connectedNodeDisplayName = MutableStateFlow("none")
    val nodeName = _connectedNodeDisplayName.asStateFlow()
    private val _pingSuccess = MutableStateFlow(false)
    val appActive = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    // AUDIO State Flows
    private val _audioStreamState = MutableStateFlow(false)
    val audioStreamState = _audioStreamState.asStateFlow()

    private val _audioBroadcastHz = MutableStateFlow(0.0F)
    val audioBroadcastHz = _audioBroadcastHz.asStateFlow()

    private val _audioStreamQueue = MutableStateFlow(0)
    val audioStreamQueue = _audioStreamQueue.asStateFlow()

    // IMU State Flows
    private val _imuStreamState = MutableStateFlow(false)
    val imuStreamState = _imuStreamState.asStateFlow()

    private val _imuInHz = MutableStateFlow(0.0F)
    val imuInHz = _imuInHz.asStateFlow()

    private val _imuOutHz = MutableStateFlow(0.0F)
    val imuOutHz = _imuOutHz.asStateFlow()

    private val _imuQueueSize = MutableStateFlow(0)
    val imuQueueSize = _imuQueueSize.asStateFlow()

    // PPG State Flows
    private val _ppgStreamState = MutableStateFlow(false)
    val ppgStreamState = _ppgStreamState.asStateFlow()

    private val _ppgInHz = MutableStateFlow(0.0F)
    val ppgInHz = _ppgInHz.asStateFlow()

    private val _ppgOutHz = MutableStateFlow(0.0F)
    val ppgOutHz = _ppgOutHz.asStateFlow()

    private val _ppgQueueSize = MutableStateFlow(0)
    val ppgQueueSize = _ppgQueueSize.asStateFlow()

    // Media Button-controlled data collection
    // deep copy the label sequence list
    private var _mediaButtonRecordingSequence =
        DataSingleton.mediaButtonRecordingSequence.toMutableList()


    /**
     * Resets the label sequence for Media Button-controlled data collection.
     */
    fun resetMediaButtonRecordingSequence() {
        // deep copy the list
        _mediaButtonRecordingSequence = DataSingleton.mediaButtonRecordingSequence.toMutableList()
        DataSingleton.setRecordActivityLabel("START")

        // send a trigger message to the connected watch app clearing all labels
        _scope.launch {
            val buffer = ByteBuffer.allocate(8) // [cur label, nxt label]
            buffer.putInt(-1)
            buffer.putInt(_mediaButtonRecordingSequence.getOrElse(0) { -1 })
            _messageClient.sendMessage( // trigger watch calibration and streaming
                _connectedNodeId,
                DataSingleton.RECORDING_LABEL_CHANGED,
                buffer.array()
            ).await()
        }
    }

    /**
     * If local recording is active, pressing a Media Button on a connected bluetooth devices triggers a label change.
     * This allows to label data in settings where the phone is not accessible. For example, in the shower.
     */
    fun onMediaButtonDown() {
        // Media buttons should only affect recording labels.
        // Ignore this trigger in broadcasting mode
        if (!DataSingleton.recordLocally.value) {
            return
        }

        var labelIdx = -1
        var nextLabelIdx = -1

        if (_mediaButtonRecordingSequence.size <= 0) {
            // The sequence has ended
            DataSingleton.setRecordActivityLabel("END")
            Log.d(TAG, "Finished recording label sequence")
            sendImuTrigger(start = false) // stop watch IMU streaming
        } else {
            // Iterate through the labels and update the data recording
            labelIdx = _mediaButtonRecordingSequence.removeAt(0)
            nextLabelIdx = _mediaButtonRecordingSequence.getOrElse(0) { -1 }
            // update UI feedback
            val label = DataSingleton.activityLabels[labelIdx]
            DataSingleton.setRecordActivityLabel(label)
            Log.d(TAG, "Updated Recording Label to $label")
            sendImuTrigger(start = true) // start watch IMU streaming
        }


        // send a message to the connected watch app informing it
        // about the current and upcoming label
        _scope.launch {
            val buffer = ByteBuffer.allocate(8) // [cur label, nxt label]
            buffer.putInt(labelIdx)
            buffer.putInt(nextLabelIdx)
            _messageClient.sendMessage( // trigger watch calibration and streaming
                _connectedNodeId,
                DataSingleton.RECORDING_LABEL_CHANGED,
                buffer.array()
            ).await()
        }
    }

    /**
     * The service update gets called in a regular interval. It updates transmission frequencies and
     * other service activities
     */
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

    /**
     * Check if the watch responds by sending a ping request
     */
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

        Log.d(TAG, "Received from: ${messageEvent.sourceNodeId} with path ${messageEvent.path}")
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

            // reset self-calibration count and skip calibration activity
            DataSingleton.CALIBRATION_PATH -> {
                val b = ByteBuffer.wrap(messageEvent.data)
                // Watch calibration data
                DataSingleton.setWatchForwardQuat(
                    floatArrayOf(
                        b.getFloat(0), b.getFloat(4),
                        b.getFloat(8), b.getFloat(12)
                    )
                )
                DataSingleton.setWatchRelPres(b.getFloat(16))

                // now check the mode
                when (b.getInt(20)) {
                    2 -> { // Mode 2: self-calibration with immediate reply to watch
                        DataSingleton.calib_count = 0
                        _scope.launch {
                            val buffer = ByteBuffer.allocate(4) // [mode (int)]
                            buffer.putInt(0)
                            _messageClient.sendMessage( // send a reply to trigger the watch streaming
                                messageEvent.sourceNodeId,
                                DataSingleton.CALIBRATION_PATH,
                                buffer.array()
                            ).await()
                        }
                    }
                }
            }
        }
    }

    /**
     * sends a trigger to the watch to start/end IMU streaming
     * The input flag "start" defines whether IMU streaming should be started or ended.
     * If no flag is provided, switch the streaming off or on (streaming = !streaming)
     */
    fun sendImuTrigger(start: Boolean? = null) {
        _scope.launch {
            val buffer = ByteBuffer.allocate(4) // [mode (int)]
            if (start == null) {
                buffer.putInt(5) // mode 5 turn off IMU stream if running, otherwise, start it
            } else if (start) {
                buffer.putInt(3) // mode 3 == start the IMU stream
            } else {
                buffer.putInt(4) // mode 4 == end the IMU stream
            }
            _messageClient.sendMessage( // trigger watch calibration and streaming
                _connectedNodeId,
                DataSingleton.CALIBRATION_PATH,
                buffer.array()
            ).await()
        }
    }

    override fun onCleared() {
        super.onCleared()
        _scope.cancel()
        Log.d(TAG, "Cleared")
    }
}