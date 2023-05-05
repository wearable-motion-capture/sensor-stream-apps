package com.mocap.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


enum class SensorStreamState {
    Idle, // app waits for user to trigger the streaming
    Error, // error state. Stop using the app,
    Streaming // streaming to Phone
}

enum class PpgStreamState {
    Idle,
    Streaming
}

enum class AudioStreamState {
    Idle, // app waits for watch to trigger the streaming
    Error, // error state. Stop streaming
    Streaming // streaming to IP and Port set in StateModule
}

object DataSingleton {

    const val VERSION = "0.2.1"

    // message paths
    const val IMU_CHANNEL_PATH = "/imu_channel"
    const val PPG_CHANNEL_PATH = "/ppg_channel"
    const val AUDIO_CHANNEL_PATH = "/audio_channel"
    const val CALIBRATION_PATH = "/calibration"
    const val PING_REQ = "/ping_request"
    const val PING_REP = "/ping_reply"
    const val BROADCAST_CLOSE = "mocap.broadcast.close"
    const val BROADCAST_UPDATE = "mocap.broadcast.update"
    const val BROADCAST_SERVICE_KEY = "service.id"
    const val BROADCAST_SERVICE_HZ = "service.hz"
    const val BROADCAST_SERVICE_QUEUE = "service.queue"

    // capabilities
    const val PHONE_APP_ACTIVE = "phone_app" // indicates if the phone app is active
    const val WATCH_APP_ACTIVE = "watch_app" // indicates if the watch app is active
    const val PHONE_CAPABILITY = "phone" // if the phone app is connected (see res/values/wear.xml)

    // streaming parameters
    const val IP_KEY = "com.mocap.watch.ip" // shared preferences lookup
    const val IP_DEFAULT = "192.168.0.12"
    const val IMU_MSG_SIZE = 14 // floats
    const val PPG_MSG_SIZE = 16 // floats
    const val STREAM_INTERVAL = 10L // floats
    const val UDP_IMU_PORT = 46000
    const val AUDIO_BUFFER_SIZE = 800
    const val UDP_AUDIO_PORT = 46001

    // as state flow to update UI elements when IP changes
    private val ipStateFlow = MutableStateFlow(IP_DEFAULT)
    val IP = ipStateFlow.asStateFlow()
    fun setIp(ip: String) {
        ipStateFlow.value = ip
    }

    // as state flow to update UI elements when value changes
    // magnetic north pole direction in relation to body orientation
    private val calibNorthStateFlow = MutableStateFlow(0.0)
    val CALIB_NORTH = calibNorthStateFlow.asStateFlow()
    fun setCalibNorth(deg: Double) {
        calibNorthStateFlow.value = deg
    }

    // as state flow to update UI elements when value changes
    // the initial pressure for relative pressure estimations
    private val calibPressStateFlow = MutableStateFlow(0.0F)
    val CALIB_PRESS = calibPressStateFlow.asStateFlow()
    fun setCalibPress(deg: Float) {
        calibPressStateFlow.value = deg
    }

    // as state flow to update UI elements when value changes
    private val _forwardQuat = MutableStateFlow(floatArrayOf(1.0F, 0.0F, 0.0F, 0.0F))
    val forwardQuat = _forwardQuat.asStateFlow()
    fun setForwardQuat(array: FloatArray) {
        _forwardQuat.value = array
    }
}