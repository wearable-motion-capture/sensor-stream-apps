package com.mocap.watch

import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter


enum class Views {
    Calibration,
    DualMode,
    StandAlone,
    IPsetting,
    ModelSelection
}


enum class AppModes {
    Dual, // requires to be connected to a phone
    Standalone, // data collection from smartwatch only
    Select // user has to select mode
}


object GlobalState {
    const val PING_PATH = "/ping"
    const val VERSION = "0.1.1"

    private var _ip = ""

    private var _appMode = AppModes.Select

    // Flow variables trigger a re-draw of UI elements
    private val _lastPingResponse = MutableStateFlow("never")
    val lastPingResponse = _lastPingResponse.asStateFlow()

    private val _viewState = MutableStateFlow(Views.ModelSelection)
    val viewState = _viewState.asStateFlow()


    private var rotVec: FloatArray = FloatArray(5) // Rotation Vector sensor or estimation
    private var lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var accl: FloatArray = FloatArray(3) // raw acceleration
    private var grav: FloatArray = FloatArray(3) // gravity
    private var pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var hr: FloatArray = FloatArray(1) // Heart Rate
    private var hrRaw: FloatArray = FloatArray(16) // Samsung's Raw HR data
    private var gyro: FloatArray = FloatArray(3) // gyroscope
    private var magn: FloatArray = FloatArray(3) // magnetic


    fun setLastPingResponse(dateStr: String) {
        _lastPingResponse.value = dateStr
    }
}