package com.mocap.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataSingleton {
    const val CHANNEL_PATH = "/channel"
    const val PHONE_APP_ACTIVE = "phone_app" // indicates if the phone app is active
    const val WATCH_APP_ACTIVE = "watch_app" // indicates if the watch app is active
    const val PHONE_CAPABILITY =
        "phone" // if a phone with the phone app is connected (see res/values/wear.xml)
    const val WATCH_CAPABILITY =
        "watch" // if a watch with the watch app is connected (see res/values/wear.xml)
    const val WATCH_MESSAGE_SIZE = 34 // 34 floats

}