package com.mocap.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ImuPpgStreamState {
    Idle, // app waits for watch to trigger the streaming
    Error, // error state. Stop streaming
    Streaming // streaming to IP and Port set in StateModule
}

enum class SoundStreamState {
    Idle, // app waits for watch to trigger the streaming
    Error, // error state. Stop streaming
    Streaming // streaming to IP and Port set in StateModule
}

object DataSingleton {
    const val VERSION = "0.1.3"

    // message paths
    const val SENSOR_CHANNEL_PATH = "/sensor_channel"
    const val SOUND_CHANNEL_PATH = "/sound_channel"
    const val CALIBRATION_PATH = "/calibration" // calibration message path
    const val PING_REQ = "/ping_request"
    const val PING_REP = "/ping_reply"

    // capabilities
    const val PHONE_APP_ACTIVE = "phone_app" // indicates if the phone app is active
    const val WATCH_APP_ACTIVE = "watch_app" // indicates if the watch app is active
    const val WATCH_CAPABILITY = "watch" // if the watch app is connected (see res/values/wear.xml)

    // streaming parameters
    const val WATCH_MSG_SIZE = 30 // floats
    const val IP_KEY = "com.mocap.watch.ip" // shared preferences lookup
    const val IP_DEFAULT = "192.168.0.12"
    const val UDP_IMU_PORT = 65000
    const val UDP_AUDIO_PORT = 65001
    const val AUDIO_BUFFER_SIZE = 800
    const val UDP_IMU_MSG_SIZE = WATCH_MSG_SIZE + 22 // floats

    // as state flow to update UI elements when IP changes
    private val ipStateFlow = MutableStateFlow(IP_DEFAULT)
    val ip = ipStateFlow.asStateFlow()
    fun setIp(st: String) {
        ipStateFlow.value = st
    }

    // as state flow to update UI elements when value changes
    // the calibrated watch forward orientation
    private val phoneForwardQuat = MutableStateFlow(floatArrayOf(1.0F, 0.0F, 0.0F, 0.0F))
    val phoneQuat = phoneForwardQuat.asStateFlow()
    fun setPhoneForwardQuat(array: FloatArray) {
        phoneForwardQuat.value = array
    }

    // the calibrated watch forward orientation
    private val watchForwardQuat = MutableStateFlow(floatArrayOf(1.0F, 0.0F, 0.0F, 0.0F))
    val watchQuat = watchForwardQuat.asStateFlow()
    fun setWatchForwardQuat(array: FloatArray) {
        watchForwardQuat.value = array
    }

    // the calibrated watch relative pressure
    private val watchRelPres = MutableStateFlow(0.0F)
    val watchPres = watchRelPres.asStateFlow()
    fun setWatchRelPres(fl: Float) {
        watchRelPres.value = fl
    }
}
