package com.mocap.watch

import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter


enum class Views {
    Calibration,
    Home,
    IPsetting
}

enum class CalibrationState {
    Idle,
    Hold,
    Forward
}

enum class SensorDataHandlerState {
    Idle, // app waits for user to trigger the recording
    Recording, // recording sensor data into memory
    Processing, // processing sensor data from memory to CSV
    Error, // error state. Stop using the app,
    Streaming // streaming to IP and Port set in SensorViewModel
}

enum class SoundStreamState {
    Idle,
    Streaming
}


class GlobalState {
    private val _version = "0.0.9"
    private var _ip = ""

    // Flow variables trigger a re-draw of UI elements
    private val _viewState = MutableStateFlow(Views.Home)
    val viewState = _viewState.asStateFlow()

    private val _calibState = MutableStateFlow(CalibrationState.Idle)
    val calibState = _calibState.asStateFlow()

    private val _sensorStrState = MutableStateFlow(SensorDataHandlerState.Idle)
    val sensorStrState = _sensorStrState.asStateFlow()

    private val _soundStrState = MutableStateFlow(SoundStreamState.Idle)
    val soundStrState = _soundStrState.asStateFlow()

    private var rotVec: FloatArray = FloatArray(5) // Rotation Vector sensor or estimation
    private var lacc: FloatArray = FloatArray(3) // linear acceleration (without gravity)
    private var accl: FloatArray = FloatArray(3) // raw acceleration
    private var pres: FloatArray = FloatArray(1) // Atmospheric pressure in hPa (millibar)
    private var hr: FloatArray = FloatArray(1) // Heart Rate
    private var hrRaw: FloatArray = FloatArray(16) // Samsung's Raw HR data
    private var gyro: FloatArray = FloatArray(3) // gyroscope
    private var magn: FloatArray = FloatArray(3) // magnetic
    private var grav: FloatArray = FloatArray(3) // gravity

    fun setView(view: Views) {
        _viewState.value = view
    }

    fun setCalibState(state: CalibrationState) {
        _calibState.value = state
    }

    fun getCalibState(): CalibrationState {
        return _calibState.value
    }

    fun setSensorState(state: SensorDataHandlerState) {
        _sensorStrState.value = state
    }

    fun getSensorState(): SensorDataHandlerState {
        return _sensorStrState.value
    }

    fun setSoundState(state: SoundStreamState) {
        _soundStrState.value = state
    }

    fun getSoundState(): SoundStreamState {
        return _soundStrState.value
    }

    fun setIP(ip: String) {
        _ip = ip

        // permission rules only allow to write into the public shared directory
        // /storage/emulated/0/Documents/sensorrecord_ip.txt
        val fileName = "sensorrecord_ip.txt"
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val textFile = File(path, fileName)
        if (textFile.exists()) {
            textFile.delete()
        }
        val fOut = FileWriter(textFile)
        fOut.write(ip)
        fOut.flush()
        fOut.close()


        setView(Views.Home)
    }

    fun getVersion(): String {
        return _version
    }

    fun getIP(): String {
        if (_ip == "") {
            // permission rules only allow to write into the public shared directory
            // /storage/emulated/0/Documents/sensorrecord_ip.txt
            val fileName = "sensorrecord_ip.txt"
            val path =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val textFile = File(path, fileName)
            _ip = if (textFile.exists()) {
                textFile.readText() // read from file if it exists
            } else {
                "192.168.0.12" // default IP
            }

        }
        return _ip
    }

    fun getSensorReadingStream(): FloatArray {
        return rotVec +  // rotation vector[5]  is a quaternion x,y,z,w, + confidence
                lacc + // [3] linear acceleration x,y,z
                pres +  // [1] atmospheric pressure
                grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                gyro + // [3] gyro data for time series prediction
                hrRaw // [16] undocumented data from Samsung's Hr raw sensor
    }

    fun getSensorReadingRecord(): FloatArray {
        return rotVec + // rotation vector[5]  is a quaternion x,y,z,w, + confidence
                lacc + // [3] linear acceleration x,y,z
                accl + // [3] unfiltered acceleration x,y,z
                pres +  // [1] atmospheric pressure
                gyro + // [3] Angular speed around the x,y,z -axis
                magn + // [3] the ambient magnetic field in the x,y,z -axis
                grav + // [3] vector indicating the direction and magnitude of gravity x,y,z
                hr + // [1] heart rate in bpm
                hrRaw // [16] undocumented data from Samsung's Hr raw sensor
    }

    fun getGravity(): FloatArray {
        return grav
    }

    fun getPressure(): FloatArray {
        return pres
    }

    fun getRotVec(): FloatArray {
        return rotVec
    }

    // Events
    // Individual sensor reads are triggered by their onValueChanged events
    fun onLaccReadout(newReadout: FloatArray) {
        lacc = newReadout
    }

    fun onRotVecReadout(newReadout: FloatArray) {
        rotVec = newReadout // [x,y,z,w]
    }

    fun onAcclReadout(newReadout: FloatArray) {
        accl = newReadout
    }

    fun onGravReadout(newReadout: FloatArray) {
        grav = newReadout
    }

    fun onGyroReadout(newReadout: FloatArray) {
        gyro = newReadout
    }

    fun onPressureReadout(newReadout: FloatArray) {
        pres = newReadout
    }

    fun onMagnReadout(newReadout: FloatArray) {
        magn = newReadout
    }

    fun onHrReadout(newReadout: FloatArray) {
        hr = newReadout
    }

    fun onHrRawReadout(newReadout: FloatArray) {
        hrRaw = newReadout
    }
}