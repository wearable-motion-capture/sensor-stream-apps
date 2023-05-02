package com.mocap.watch.viewmodel

import android.Manifest
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import com.mocap.watch.SensorStreamState
import com.mocap.watch.SoundStreamState
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

class DualViewModel(application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "DualViewModel"  // for logging
        private const val IMU_PPG_STREAM_INTERVAL = DataSingleton.STREAM_INTERVAL
        private const val AUDIO_RATE = 16000 // can go up to 44K, if needed
        private const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)


    // this will hold the connected Phone ID
    private var _connectedNodeId: String = "none"

    private val _connectedNodeDisplayName = MutableStateFlow("No Device")
    val nodeName = _connectedNodeDisplayName.asStateFlow()

    private val _pingSuccess = MutableStateFlow(false)
    val appActive = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    private val _sensorStreamStateSensor = MutableStateFlow(SensorStreamState.Idle)
    val sensorStreamState = _sensorStreamStateSensor.asStateFlow()

    private val _soundStreamState = MutableStateFlow(SoundStreamState.Idle)
    val soundStreamState = _soundStreamState.asStateFlow()

    // callbacks will write to these variables
    private var _rotVec: FloatArray = FloatArray(4) // Rotation Vector sensor or estimation
    private var _lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var _accl: FloatArray = FloatArray(3) // raw acceleration
    private var _grav: FloatArray = FloatArray(3) // gravity
    private var _pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var _hrRaw: FloatArray = FloatArray(16) // Samsung's Raw HR data
    private var _gyro: FloatArray = FloatArray(3) // gyroscope
    private var _magn: FloatArray = FloatArray(3) // magnetic

    override fun onCleared() {
        super.onCleared()
        _scope.cancel()
        resetStreamStates()
        Log.d(TAG, "Cleared")
    }

    fun resetStreamStates() {
        _sensorStreamStateSensor.value = SensorStreamState.Idle
        _soundStreamState.value = SoundStreamState.Idle
    }

    fun onChannelClose(c: Channel) {
        // reset the corresponding stream loop
        when (c.path) {
            DataSingleton.SENSOR_CHANNEL_PATH -> _sensorStreamStateSensor.value =
                SensorStreamState.Idle

            DataSingleton.SOUND_CHANNEL_PATH -> _soundStreamState.value =
                SoundStreamState.Idle
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun audioStreamTrigger(checked: Boolean) {
        if (!checked) {
            _soundStreamState.value = SoundStreamState.Idle
        } else {
            if (_soundStreamState.value == SoundStreamState.Streaming) {
                throw Exception("The StreamState.Streaming should not be active at start")
            }
            _scope.launch {
                // Open the channel
                val channel = _channelClient.openChannel(
                    _connectedNodeId,
                    DataSingleton.SOUND_CHANNEL_PATH
                ).await()
                Log.d(TAG, "Opened ${DataSingleton.SOUND_CHANNEL_PATH} to $_connectedNodeId")
                // Create an AudioRecord object for the streaming
                val audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(AUDIO_RATE)
                            .setChannelMask(AUDIO_CHANNEL_IN)
                            .setEncoding(AUDIO_FORMAT)
                            .build()
                    )
                    .setBufferSizeInBytes(DataSingleton.AUDIO_BUFFER_SIZE)
                    .build()
                // begin streaming the microphone
                audioRecord.startRecording()
                Log.d(TAG, "Initiate audio stream")
                try {
                    // get output stream
                    val outputStream = _channelClient.getOutputStream(channel).await()
                    outputStream.use {
                        // start the stream loop
                        _soundStreamState.value = SoundStreamState.Streaming
                        while (_soundStreamState.value == SoundStreamState.Streaming) {
                            val buffer = ByteBuffer.allocate(DataSingleton.AUDIO_BUFFER_SIZE)
                            audioRecord.read(buffer.array(), 0, buffer.capacity())
                            outputStream.write(buffer.array(), 0, buffer.capacity())
                        }
                    }
                } catch (e: Exception) {
                    // In case the channel gets destroyed while still in the loop
                    _channelClient.close(channel)
                    Log.d(TAG, e.message.toString())
                    _soundStreamState.value = SoundStreamState.Error
                } finally {
                    // if the loop ends of was disrupted, close everything and reset
                    audioRecord.release()
                    _channelClient.close(channel)
                }
                Log.d(TAG, "Audio stream complete")
            }
        }
    }

    fun sensorStreamTrigger(checked: Boolean) {
        if (!checked) {
            _sensorStreamStateSensor.value = SensorStreamState.Idle
        } else {
            if (sensorStreamState.value == SensorStreamState.Streaming) {
                throw Exception("The StreamState.Streaming should not be active at start")
            }

            _scope.launch {

                // Open the channel
                val channel = _channelClient.openChannel(
                    _connectedNodeId,
                    DataSingleton.SENSOR_CHANNEL_PATH
                ).await()
                Log.d(TAG, "Opened ${DataSingleton.SENSOR_CHANNEL_PATH} to $_connectedNodeId")

                try {
                    // get output stream
                    val outputStream = _channelClient.getOutputStream(channel).await()
                    outputStream.use {
                        // start the stream loop
                        _sensorStreamStateSensor.value = SensorStreamState.Streaming
                        while (sensorStreamState.value == SensorStreamState.Streaming) {
                            // compose message as float array
                            val sensorData =
                                _rotVec + // transformed rotation vector[4] is a quaternion [w,x,y,z]
                                        _lacc + // [3] linear acceleration x,y,z
                                        _pres + // [1] atmospheric pressure
                                        _grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                                        _gyro + // [3] gyro data for time series prediction
                                        _hrRaw // [16] undocumented data from Samsung's Hr raw sensor

                            // feed into byte buffer
                            val buffer = ByteBuffer.allocate(4 * DataSingleton.WATCH_MESSAGE_SIZE)
                            for (v in sensorData) buffer.putFloat(v)

                            Log.d(TAG, _rotVec[0].toString())
                            // write to output stream
                            outputStream.write(buffer.array(), 0, buffer.capacity())
                            delay(IMU_PPG_STREAM_INTERVAL)
                        }
                    }
                } catch (e: Exception) {
                    // In case the channel gets destroyed while still in the loop
                    Log.d(TAG, e.message.toString())
                    _sensorStreamStateSensor.value = SensorStreamState.Error
                } finally {
                    _channelClient.close(channel)
                }
                Log.d(TAG, "Stream Trigger complete")
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

    /** send a ping request */
    private fun requestPing() {
        _scope.launch {
            _messageClient.sendMessage(_connectedNodeId, DataSingleton.PING_REQ, null).await()
            delay(1000L)
            // reset success indicator if the response takes too long
            if (Duration.between(_lastPing, LocalDateTime.now()).toMillis() > 1100L) {
                _pingSuccess.value = false
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

    // Events
    /** sensor callbacks */
    // Individual sensor reads are triggered by their onValueChanged events
    fun onLaccReadout(newReadout: FloatArray) {
        _lacc = newReadout
    }

    fun onRotVecReadout(newReadout: FloatArray) {
        // newReadout is [x,y,z,w, confidence]
        // our preferred order system is [w,x,y,z]
        _rotVec = floatArrayOf(
            newReadout[3],
            newReadout[0],
            newReadout[1],
            newReadout[2]
        )
    }

    fun onAcclReadout(newReadout: FloatArray) {
        _accl = newReadout
    }

    fun onGyroReadout(newReadout: FloatArray) {
        _gyro = newReadout
    }

    fun onMagnReadout(newReadout: FloatArray) {
        _magn = newReadout
    }

    fun onHrRawReadout(newReadout: FloatArray) {
        _hrRaw = newReadout
    }

    fun onPressureReadout(newReadout: FloatArray) {
        _pres = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        _grav = newReadout
    }

}
