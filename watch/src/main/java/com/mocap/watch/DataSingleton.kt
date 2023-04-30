package com.mocap.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataSingleton {
    const val CHANNEL_PATH = "/channel"
    const val CALIBRATION_PATH = "/calibration"
    const val IP_KEY = "com.mocap.watch.ip" // shared preferences lookup
    const val IP_DEFAULT = "192.168.0.12"
    const val VERSION = "0.1.5"
    const val PHONE_APP_ACTIVE = "phone_app" // indicates if the phone app is active
    const val WATCH_APP_ACTIVE = "watch_app" // indicates if the watch app is active
    const val WATCH_MESSAGE_SIZE = 31 // floats
    const val STREAM_INTERVAL = 20L // floats
    const val PHONE_CAPABILITY =
        "phone" // if a phone with the phone app is connected (see res/values/wear.xml)
    const val WATCH_CAPABILITY =
        "watch" // if a watch with the watch app is connected (see res/values/wear.xml)

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