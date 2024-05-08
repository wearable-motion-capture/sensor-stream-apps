package com.mocap.watch.activity

import android.content.IntentFilter
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.ServiceBroadcastReceiver
import com.mocap.watch.modules.WatchChannelCallback
import com.mocap.watch.ui.theme.WatchTheme
import com.mocap.watch.ui.view.RenderSelfLabelling
import com.mocap.watch.viewmodel.SelfLabellingViewModel


class SelfLabellingActivity : ComponentActivity(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "SelfLabelling"  // for logging
//        private const val REQCODE_WATCHTOUCH = 0
//        private const val PERMISSION_WATCH_TOUCH =
//            "com.google.android.clockwork.settings.WATCH_TOUCH"
//
//        private const val ACTION_ENABLE_WET_MODE =
//            "com.google.android.wearable.action.ENABLE_WET_MODE"
    }

    private val _channelClient by lazy { Wearable.getChannelClient(this) }
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _viewModel by viewModels<SelfLabellingViewModel>()
    private val _channelCallback = WatchChannelCallback(
        closeCallback = { _viewModel.onChannelClose(it) }
    )
    private val _br =
        ServiceBroadcastReceiver(
            onServiceClose = { _viewModel.onServiceClose(it) },
            onServiceUpdate = { _viewModel.onServiceUpdate(it) }
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

// LEFTOVER FROM WET MODE EXPERIMENTS TODO: Cleanup
//            if (PermissionChecker.checkSelfPermission(
//                    this,
//                    PERMISSION_WATCH_TOUCH
//                ) != PermissionChecker.PERMISSION_GRANTED
//            ) {
//                requestPermissions(arrayOf(PERMISSION_WATCH_TOUCH), REQCODE_WATCHTOUCH)
//            } else {
//                Log.d(TAG, "touch lock permission granted")
//            }
//            val count = WearableButtons.getButtonCount(this.baseContext)
//            if (count > 1) {
//                Log.d(TAG, "there are ${count} buttons available")
//            }
//            val buttonInfo = WearableButtons.getButtonInfo(this, KeyEvent.KEYCODE_STEM_1)
//            Log.d(TAG, "KEYCODE_STEM_1 is present on the device")

            // with the vibration service, create the view model
            _viewModel.queryCapabilities()
            _viewModel.regularConnectionCheck()
            registerListeners()

            // keep screen on to not enter ambient mode
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WatchTheme {
                RenderSelfLabelling(
                    connected = _viewModel.pingSuccessState,
                    connectedNodeName = _viewModel.nodeName,
                    imuStreamStateFlow = _viewModel.sensorStreamState,
                    curLabelSF = _viewModel.curLabel,
                    nxtLabelSF = _viewModel.nxtLabel,
                    finishCallback = ::finish
                )
            }
        }
    }


    /**
     * Catch BACK KEY events
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "BACK KEY is down")
            return true
        }
        return false
    }

//    /**
//     * Use WET MODE
//     */
//    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
//        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            _viewModel.keyTrigger()
//            Log.d(TAG, "Starting wet mode...")
//            sendBroadcast(Intent(ACTION_ENABLE_WET_MODE))
//            return true
//        }
//        return false
//    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        _viewModel.onMessageReceived(messageEvent)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _viewModel.onCapabilityChanged(capabilityInfo)
    }

    /**
     * Register all listeners with their assigned codes.
     * Called on app startup and whenever app resumes
     */
    private fun registerListeners() {
        // broadcasts inform about stopped services
        val filter = IntentFilter()
        filter.addAction(DataSingleton.BROADCAST_CLOSE)
        filter.addAction(DataSingleton.BROADCAST_UPDATE)
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(_br, filter)

        // listen for ping messages
        _messageClient.addListener(this)
        // to handle incoming data streams
        _channelClient.registerChannelCallback(_channelCallback)
        // for the phone app to detect this phone
        _capabilityClient.addLocalCapability(DataSingleton.WATCH_APP_ACTIVE)
        // checks for a connected device with the Phone capability
        _capabilityClient.addListener(
            this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        _viewModel.queryCapabilities()
    }

    /**
     * Unregister listeners and cancel vibration signals when exiting
     * the calibration activity
     */
    private fun unregisterListeners() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
        _viewModel.resetAllStreamStates()
        _messageClient.removeListener(this)
        _channelClient.unregisterChannelCallback(_channelCallback)
        _capabilityClient.removeListener(this)
        _capabilityClient.removeLocalCapability(DataSingleton.WATCH_APP_ACTIVE)
        this.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterListeners()
    }

    override fun onPause() {
        super.onPause()
        unregisterListeners()
    }

    override fun onResume() {
        super.onResume()
        registerListeners()
    }
}
