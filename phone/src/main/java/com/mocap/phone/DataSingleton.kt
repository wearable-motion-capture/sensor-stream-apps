package com.mocap.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataSingleton {
    const val VERSION = "0.2.0"

    // message paths
    const val IMU_CHANNEL_PATH = "/imu_channel"
    const val PPG_CHANNEL_PATH = "/ppg_channel"
    const val AUDIO_CHANNEL_PATH = "/audio_channel"
    const val CALIBRATION_PATH = "/calibration" // calibration message path
    const val PING_REQ = "/ping_request"
    const val PING_REP = "/ping_reply"

    const val BROADCAST_UPDATE = "mocap.broadcast.update"
    const val BROADCAST_SERVICE_KEY = "service.id"
    const val BROADCAST_SERVICE_STATE = "service.state"
    const val BROADCAST_SERVICE_HZ_IN = "service.hz.in"
    const val BROADCAST_SERVICE_HZ_OUT = "service.hz.out"
    const val BROADCAST_SERVICE_QUEUE = "service.queue"

    // capabilities
    const val PHONE_APP_ACTIVE = "phone_app" // indicates if the phone app is active
    const val WATCH_CAPABILITY = "watch" // if the watch app is connected (see res/values/wear.xml)
    const val PHONE_CAPABILITY = "phone"

    // streaming parameters
    const val IMU_MSG_SIZE = (4 + 17) * 4 // timestamp(4) + data (14 float)
    const val PPG_MSG_SIZE = (4 + 16) * 4 // timestamp(4) + data (16 float)
    const val IP_KEY = "com.mocap.watch.ip" // shared preferences lookup
    const val IP_DEFAULT = "192.168.0.12"
    const val UDP_IMU_PORT = 65000
    const val UDP_AUDIO_PORT = 65001
    const val UDP_PPG_PORT = 65002
    const val AUDIO_BUFFER_SIZE = 800
    const val DUAL_IMU_MSG_SIZE = IMU_MSG_SIZE + (4 + 26) * 4 // timestamp(4) + data (22 float)

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
