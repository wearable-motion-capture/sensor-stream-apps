package com.mocap.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataSingleton {
    const val VERSION = "0.3.6"

    // message paths
    const val IMU_PATH = "/imu"
    const val PPG_PATH = "/ppg"
    const val AUDIO_PATH = "/audio"
    const val CALIBRATION_PATH = "/calibration" // calibration message path
    const val PING_REQ = "/ping_request"
    const val PING_REP = "/ping_reply"

    // UI broadcasts
    const val BROADCAST_UPDATE = "mocap.broadcast.update"
    const val BROADCAST_SERVICE_KEY = "service.id"
    const val BROADCAST_SERVICE_STATE = "service.state"
    const val BROADCAST_SERVICE_HZ_IN = "service.hz.in"
    const val BROADCAST_SERVICE_HZ_OUT = "service.hz.out"
    const val BROADCAST_SERVICE_QUEUE = "service.queue"

    // capabilities
    const val PHONE_APP_ACTIVE = "phone_app" // indicates if the phone app is active
    const val WATCH_CAPABILITY = "watch" // if the watch app is connected (see res/values/wear.xml)

    // self calibration
    const val SELF_CALIB_END = 10
    var calib_count = SELF_CALIB_END // reset this counter if you want to self-calibrate IMU

    // streaming parameters
    const val IMU_MSG_SIZE = (5 + 23) * 4 // deltaT + timestamp(4) + data (22 float)
    const val PPG_MSG_SIZE = (4 + 16) * 4 // timestamp(4) + data (16 float)
    const val IMU_PORT_LEFT = 65000
    const val IMU_PORT_RIGHT = 65003
    const val UDP_AUDIO_PORT = 65001
    const val UDP_PPG_PORT = 65002
    const val AUDIO_BUFFER_SIZE = 2048
    const val DUAL_IMU_MSG_SIZE = (55) * 4 // dT + SW IMU msg without calib + PH IMU MSG

    // recording parameters
    val activityOptions = listOf<String>(
        "other",
        "brush_teeth",
        "shave",
        "deodorant",
        "wash_hands",
        "lotion",
        "hairstyling"
    )

    // shared preferences lookup
    const val IP_KEY = "com.mocap.phone.ip"
    const val IP_DEFAULT = "192.168.1.138"
    const val PORT_KEY = "com.mocap.phone.port"
    const val IMU_PORT_DEFAULT = IMU_PORT_LEFT
    const val RECORD_LOCALLY_KEY = "com.mocap.phone.record_locally"
    const val RECORD_LOCALLY_DEFAULT = false

    private val recordActivityNameCombinedStateFlow = MutableStateFlow(activityOptions[0])
    val recordActivityNameCombined = recordActivityNameCombinedStateFlow.asStateFlow()

    // For recording sequences we record activity A ...
    private val recordActivityNameAStateFlow = MutableStateFlow(activityOptions[0])
    val recordActivityNameA = recordActivityNameAStateFlow.asStateFlow()
    fun setRecordActivityNameA(st: String) {
        recordActivityNameAStateFlow.value = st
        if (st.equals(recordActivityNameBStateFlow.value)) {
            recordActivityNameCombinedStateFlow.value = st
        } else {
            recordActivityNameCombinedStateFlow.value =
                "seq_$st--${recordActivityNameBStateFlow.value}"
        }
    }

    // ... and activity B. See method above.
    private val recordActivityNameBStateFlow = MutableStateFlow(activityOptions[0])
    val recordActivityNameB = recordActivityNameBStateFlow.asStateFlow()
    fun setRecordActivityNameB(st: String) {
        recordActivityNameBStateFlow.value = st
        if (st.equals(recordActivityNameAStateFlow.value)) {
            recordActivityNameCombinedStateFlow.value = st
        } else {
            recordActivityNameCombinedStateFlow.value =
                "seq_${recordActivityNameAStateFlow.value}--$st"
        }
    }


    // as state flow to update UI elements when IP changes
    private val ipStateFlow = MutableStateFlow(IP_DEFAULT)
    val ip = ipStateFlow.asStateFlow()
    fun setIp(st: String) {
        ipStateFlow.value = st
    }

    // this flag decides whether the phone broadcasts or records
    private val recordLocallyStateFlow = MutableStateFlow(false)
    val recordLocally = recordLocallyStateFlow.asStateFlow()
    fun setRecordLocally(b: Boolean) {
        recordLocallyStateFlow.value = b
    }

    // as state flow to update UI elements when IP changes
    private val portStateFlow = MutableStateFlow(IMU_PORT_DEFAULT)
    val imuPort = portStateFlow.asStateFlow()
    fun setImuPort(p: Int) {
        portStateFlow.value = p
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
