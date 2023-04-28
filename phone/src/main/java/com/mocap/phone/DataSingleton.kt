package com.mocap.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataSingleton {
    const val CHANNEL_PATH = "/channel" // channel message path
    const val CALIBRATION_PATH = "/calibration" // calibration message path
    const val PHONE_APP_ACTIVE = "phone_app" // indicates if the phone app is active
    const val WATCH_APP_ACTIVE = "watch_app" // indicates if the watch app is active
    const val PHONE_CAPABILITY = "phone" // if the phone app is connected (see res/values/wear.xml)
    const val WATCH_CAPABILITY = "watch" // if the watch app is connected (see res/values/wear.xml)
    const val WATCH_MESSAGE_SIZE = 34 // 34 floats

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
    fun setWatchRelPres(v: Float) {
        watchRelPres.value = v
    }
}