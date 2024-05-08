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
    const val VERSION = "0.4.1"

    // dual mode communication paths
    const val IMU_PATH = "/imu"
    const val PPG_PATH = "/ppg"
    const val AUDIO_PATH = "/audio"
    const val CALIBRATION_PATH = "/calibration"
    const val RECORDING_LABEL_CHANGED = "/rec_label_changed"
    const val PING_REQ = "/ping_request"
    const val PING_REP = "/ping_reply"
    const val BROADCAST_CLOSE = "mocap.broadcast.close"
    const val BROADCAST_SERVICE_KEY = "service.id"
    const val BROADCAST_UPDATE = "mocap.broadcast.update"

    // shared preferences for saved settings
    const val EXP_MODE_KEY = "mocap.exp_mode"
    const val EXP_MODE_KEY_DEFAULT = false

    // capabilities
    const val PHONE_APP_ACTIVE = "phone_app" // indicates if the phone app is active
    const val WATCH_APP_ACTIVE = "watch_app" // indicates if the watch app is active
    const val PHONE_CAPABILITY = "phone" // if the phone app is connected (see res/values/wear.xml)

    // streaming parameters
    const val IMU_MSG_SIZE = (5 + 23) * 4
    const val PPG_MSG_SIZE = (4 + 16) * 4 // timestamp(4) + data (16 float)
    const val AUDIO_BUFFER_SIZE = 2048 // bytes

    // standalone mode
    const val UDP_IMU_PORT = 46000
    const val UDP_AUDIO_PORT = 65001
    const val IP_DEFAULT = "192.168.0.12"
    const val IP_KEY = "com.mocap.watch.ip" // shared preferences lookup

    // display recording activity labels in self-labelling mode
    // these labels must be identical to the DataSingleton on the phone
    val activityLabels = listOf<String>(
        "other", // 0
        "still", // 1
        "brush_teeth", // 2
        "shave_face", // 3
        "deodorant", // 4
        "wash_hands", // 5
        "lotion", // 6
        "hairstyling", // 7
        "wash_hair", // 8
        "shave_legs", // 9
        "soap_body" // 10
    )

    // experimental mode enables additional options in the WatchModeSelection
    private val experimentalModeSF = MutableStateFlow(false)
    val expMode = experimentalModeSF.asStateFlow()
    fun setExpMode(b: Boolean) {
        experimentalModeSF.value = b
    }

    // as state flow to update UI elements when IP changes
    private val ipStateFlow = MutableStateFlow(IP_DEFAULT)
    val ip = ipStateFlow.asStateFlow()
    fun setIp(ip: String) {
        ipStateFlow.value = ip
    }

    // as state flow to update UI elements when value changes
    // the initial pressure for relative pressure estimations
    private val calibPressStateFlow = MutableStateFlow(0.0F)
    val calib_pres = calibPressStateFlow.asStateFlow()
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