//import android.hardware.Sensor
//import com.mocap.watch.modules.SensorListener
//
////package com.mocap.watch.activity
////
////import android.os.*
////import androidx.activity.ComponentActivity
////import androidx.activity.compose.setContent
////import com.mocap.watch.modules.PingRequester
////import com.mocap.watch.ui.theme.WatchTheme
////import com.mocap.watch.ui.view.RenderDualMode
////
////
////open class DualActivity : ComponentActivity() {
////
////    companion object {
////        private const val TAG = "DualActivity"  // for logging
////    }
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////
////        setContent {
////            // for colours
////            WatchTheme {
////
////                val pingRequester = PingRequester(context = applicationContext)
////
////                RenderDualMode(
////                    sensorDataHandler = ,
////                    soundStreamer = ,
////                    calibrator = ,
////                    pingRequester =
////                )
////
////            }
////        }
////    }
////
////    override fun onResume() {
////        super.onResume()
////    }
////
////    override fun onPause() {
////        super.onPause()
////    }
////}
//
//
//protected var _listenersSetup = listOf(
//    SensorListener(
//        Sensor.TYPE_PRESSURE
//    ) { GlobalState.onPressureReadout(it) },
//    SensorListener(
//        Sensor.TYPE_LINEAR_ACCELERATION
//    ) { GlobalState.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
//    SensorListener(
//        Sensor.TYPE_ACCELEROMETER
//    ) { GlobalState.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
//    SensorListener(
//        Sensor.TYPE_ROTATION_VECTOR
//    ) { GlobalState.onRotVecReadout(it) },
//    SensorListener(
//        Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
//    ) { GlobalState.onMagnReadout(it) },
//    SensorListener(
//        Sensor.TYPE_GRAVITY
//    ) { GlobalState.onGravReadout(it) },
//    SensorListener(
//        Sensor.TYPE_GYROSCOPE
//    ) { GlobalState.onGyroReadout(it) },
////        SensorListener(
////            Sensor.TYPE_HEART_RATE
////        ) { globalState.onHrReadout(it) },
//    SensorListener(
//        69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
//    ) { GlobalState.onHrRawReadout(it) }
//)
//
////    private var _listenersSetup = listOf(
////        DebugSensorListener(
////            34 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
////        )
////    )