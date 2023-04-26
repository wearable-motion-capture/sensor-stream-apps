package com.mocap.watch.activity

import android.content.Intent
import android.hardware.Sensor
import android.net.Uri
import com.mocap.watch.stateModules.DualModule
import com.mocap.watch.stateModules.SensorListener
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderDualMode
import com.mocap.watch.viewmodel.ChannelViewModel
import com.mocap.watch.viewmodel.PingViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


class DualActivity : SensorActivity() {

    companion object {
        private const val TAG = "DualActivity"  // for logging
    }

    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _pingViewModel by viewModels<PingViewModel>()
    private val _channelViewModel by viewModels<ChannelViewModel>()
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    // private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // for colours
            WatchTheme {

                val dualModule = DualModule()
                // make use of the SensorActivity management
                createListeners(dualModule)

                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                val pingStateFlow = _pingViewModel.connected

                RenderDualMode(
                    pingStateFlow = pingStateFlow,
                    pingCallback = _pingViewModel::requestPing,
                    calibCallback = { startActivity(Intent("com.mocap.watch.activity.Calibration")) },
                    queryDeviceCallback = ::onQueryOtherDevicesClicked,
                    finishCallback = ::finish
                )
            }
        }
    }


    private fun onQueryOtherDevicesClicked() {
        lifecycleScope.launch {
            try {
                val nodes = getCapabilitiesForReachableNodes()
                val filterd = nodes.filterValues { "mobile" in it || "wear" in it }.keys
                displayNodes(filterd)
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(TAG, "Querying nodes failed: $exception")
            }
        }
    }

    /**
     * Collects the capabilities for all nodes that are reachable using the [CapabilityClient].
     *
     * [CapabilityClient.getAllCapabilities] returns this information as a [Map] from capabilities
     * to nodes, while this function inverts the map so we have a map of [Node]s to capabilities.
     *
     * This form is easier to work with when trying to operate upon all [Node]s.
     */
    private suspend fun getCapabilitiesForReachableNodes(): Map<Node, Set<String>> =
        capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
            .await()
            // Pair the list of all reachable nodes with their capabilities
            .flatMap { (capability, capabilityInfo) ->
                capabilityInfo.nodes.map { it to capability }
            }
            // Group the pairs by the nodes
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )
            // Transform the capability list for each node into a set
            .mapValues { it.value.toSet() }


    private fun displayNodes(nodes: Set<Node>) {
        val message = if (nodes.isEmpty()) {
            "no devices"
        } else {
            nodes.joinToString(", ") { it.displayName }
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    private fun createListeners(stateModule: DualModule) {
        super.setSensorListeners(
            listOf(
                SensorListener(
                    Sensor.TYPE_PRESSURE
                ) { stateModule.onPressureReadout(it) },
                SensorListener(
                    Sensor.TYPE_LINEAR_ACCELERATION
                ) { stateModule.onLaccReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), excluding the force of gravity.
                SensorListener(
                    Sensor.TYPE_ACCELEROMETER
                ) { stateModule.onAcclReadout(it) }, // Measures the acceleration force in m/s2 that is applied to a device on all three physical axes (x, y, and z), including the force of gravity.
                SensorListener(
                    Sensor.TYPE_ROTATION_VECTOR
                ) { stateModule.onRotVecReadout(it) },
                SensorListener(
                    Sensor.TYPE_MAGNETIC_FIELD // All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and Z axis.
                ) { stateModule.onMagnReadout(it) },
                SensorListener(
                    Sensor.TYPE_GRAVITY
                ) { stateModule.onGravReadout(it) },
                SensorListener(
                    Sensor.TYPE_GYROSCOPE
                ) { stateModule.onGyroReadout(it) },
//        SensorListener(
//            Sensor.TYPE_HEART_RATE
//        ) { globalState.onHrReadout(it) },
                SensorListener(
                    69682 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked
                ) { stateModule.onHrRawReadout(it) }
            )
        )
//            34 // Samsung HR Raw Sensor this is the only Galaxy5 raw sensor that worked

    }

    override fun onResume() {
        super.onResume()
        _messageClient.addListener(_pingViewModel)
        capabilityClient.addListener(
            _channelViewModel,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
    }

    override fun onPause() {
        super.onPause()
        _messageClient.removeListener(_pingViewModel)
        capabilityClient.removeListener(_channelViewModel)
    }


}
