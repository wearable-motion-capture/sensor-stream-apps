package com.mocap.watch

object Constants {
    const val PING_PATH = "/ping"
    const val VERSION = "0.1.1"

    var IP = "192.168.1.138"

    // set by the calibration module
    var CALIB_NORTH = 0.0 // magnetic north pole direction in relation to body orientation
    var CALIB_PRESS = 0.0  // the initial pressure for relative pressure estimations
}