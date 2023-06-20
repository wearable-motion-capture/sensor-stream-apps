package com.mocap.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


enum class ImuStreamState {
    Idle, // app waits for user to trigger the streaming
    Streaming // streaming to Phone
}

enum class PpgStreamState {
    Idle,
    Streaming
}

enum class AudioStreamState {
    Idle, // app waits for watch to trigger the streaming
    Streaming // streaming to IP and Port set in StateModule
}

object DataSingleton {

    const val VERSION = "0.2.6"

    // dual mode communication paths
    const val IMU_CHANNEL_PATH = "/imu_channel"
    const val IMU_UDP_PATH = "/imu_udp"
    const val PPG_CHANNEL_PATH = "/ppg_channel"
    const val AUDIO_CHANNEL_PATH = "/audio_channel"
    const val AUDIO_UDP_PATH = "/audio_udp"
    const val CALIBRATION_PATH = "/calibration"
    const val PING_REQ = "/ping_request"
    const val PING_REP = "/ping_reply"
    const val BROADCAST_CLOSE = "mocap.broadcast.close"
    const val BROADCAST_SERVICE_KEY = "service.id"

    // TODO: implement frequency outputs on watch
    const val BROADCAST_UPDATE = "mocap.broadcast.update"
    const val BROADCAST_SERVICE_HZ = "service.hz"
    const val BROADCAST_SERVICE_QUEUE = "service.queue"

    // capabilities
    const val PHONE_APP_ACTIVE = "phone_app" // indicates if the phone app is active
    const val WATCH_APP_ACTIVE = "watch_app" // indicates if the watch app is active
    const val PHONE_CAPABILITY = "phone" // if the phone app is connected (see res/values/wear.xml)
    const val WATCH_CAPABILITY = "watch"

    // streaming parameters
    const val IMU_CHANNEL_MSG_SIZE = (4 + 14) * 4 // timestamp(4) + data (14 float)
    const val IMU_UDP_MSG_SIZE = (4 + 19) * 4 // timestamp(4) + data (16 float) (calibration)
    const val PPG_MSG_SIZE = (4 + 16) * 4 // timestamp(4) + data (16 float)
    const val AUDIO_BUFFER_SIZE = 800 // bytes

    // standalone mode
    const val UDP_IMU_PORT = 46000
    const val UDP_AUDIO_PORT = 46001
    const val IP_DEFAULT = "192.168.0.12"
    const val IP_KEY = "com.mocap.watch.ip" // shared preferences lookup

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