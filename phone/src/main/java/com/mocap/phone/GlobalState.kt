package com.mocap.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object GlobalState {
    const val PING_PATH = "/ping"

    // Flow variables trigger a re-draw of UI elements
    private val _lastPingResponse = MutableStateFlow("never")
    val lastPingResponse = _lastPingResponse.asStateFlow()


    fun setLastPingResponse(dateStr: String) {
        _lastPingResponse.value = dateStr
    }

}