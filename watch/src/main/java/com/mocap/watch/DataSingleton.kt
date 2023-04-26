package com.mocap.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataSingleton {
    const val PING_PATH = "/ping" // message path
    const val CHANNEL_PATH = "/channel"
    const val IP_KEY = "com.mocap.watch.ip" // shared preferences lookup
    const val IP_DEFAULT = "192.168.0.12"
    const val VERSION = "0.1.1"

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
    private val calibPressStateFlow = MutableStateFlow(0.0)
    val CALIB_PRESS = calibPressStateFlow.asStateFlow()
    fun setCalibPress(deg: Double) {
        calibPressStateFlow.value = deg
    }
}