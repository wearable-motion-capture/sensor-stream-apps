package com.mocap.phone.activity

import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaSession
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.mocap.phone.DataSingleton
import com.mocap.phone.modules.ServiceBroadcastReceiver
import com.mocap.phone.service.AudioService
import com.mocap.phone.service.ImuService
import com.mocap.phone.service.PpgService
import com.mocap.phone.ui.theme.PhoneTheme
import com.mocap.phone.ui.view.RenderHome
import com.mocap.phone.modules.MediaSessionButtonsCallback
import com.mocap.phone.viewmodel.PhoneViewModel
import java.nio.ByteBuffer

class PhoneMain : ComponentActivity(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "PhoneMainActivity"
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _viewModel by viewModels<PhoneViewModel>()

    private val _br =
        ServiceBroadcastReceiver(
            onServiceUpdate = { _viewModel.onServiceUpdate(it) }
        )

    /** listen to media buttons to affect recording sessions **/
    private lateinit var _mediaSession: MediaSession
    private val _callback = MediaSessionButtonsCallback(
        onTrigger = { _viewModel.onMediaButtonDown() }
    )

    /** Starting point of the application */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

            // retrieve stored values from shared preferences and update DataSingleton
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            val storedIp = sharedPref.getString(DataSingleton.IP_KEY, DataSingleton.IP_DEFAULT)
            if (storedIp != null) {
                DataSingleton.setIp(storedIp)
            }

            val addFileId = sharedPref.getString(
                DataSingleton.ADD_FILE_ID_KEY, DataSingleton.ADD_FILE_ID_DEFAULT
            )
            if (addFileId != null) {
                DataSingleton.setAddFileId(addFileId)
            }

            DataSingleton.setImuPort(
                sharedPref.getInt(
                    DataSingleton.PORT_KEY,
                    DataSingleton.IMU_PORT_DEFAULT
                )
            )

            val rl = sharedPref.getBoolean(
                DataSingleton.RECORD_LOCALLY_KEY,
                DataSingleton.RECORD_LOCALLY_DEFAULT
            )
            DataSingleton.setRecordLocally(rl)

            val mb = sharedPref.getBoolean(
                DataSingleton.MEDIA_BUTTONS_KEY,
                DataSingleton.MEDIA_BUTTONS_DEFAULT
            )
            DataSingleton.setListenToMediaButtons(mb)

            // enabling a media session allows to control recording labels with media buttons
            // Currently, the play/pause button switches the label if the phone is in recording mode
            if (rl and mb) {
                _mediaSession = MediaSession(this, TAG)
                _mediaSession.setCallback(_callback)
                _mediaSession.setActive(true)
                _viewModel.resetMediaButtonRecordingSequence()
                Log.d(TAG, "ONCREATE - Media Session Active")
            }

            PhoneTheme {
                // keep screen on
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // check if a phone is connected and set state flows accordingly
                _viewModel.queryCapabilities()
                _viewModel.regularUiUpdates()

                RenderHome(
                    connectedNodeSF = _viewModel.nodeName,
                    appActiveSF = _viewModel.appActive,
                    imuSF = _viewModel.imuStreamState,
                    imuInHzSF = _viewModel.imuInHz,
                    imuOutHzSF = _viewModel.imuOutHz,
                    imuQueueSizeSF = _viewModel.imuQueueSize,
                    audioSF = _viewModel.audioStreamState,
                    audioBroadcastHzSF = _viewModel.audioBroadcastHz,
                    audioStreamQueueSF = _viewModel.audioStreamQueue,
                    ppgSF = _viewModel.ppgStreamState,
                    ppgInHzSF = _viewModel.ppgInHz,
                    ppgOutHzSF = _viewModel.ppgOutHz,
                    ppgQueueSizeSF = _viewModel.ppgQueueSize,
                    ipSetCallback = {
                        startActivity(Intent("com.mocap.phone.SET_IP"))
                    },
                    imuStreamTrigger = {
                        _viewModel.sendImuTrigger()
                    },
                    labelCycleReset = {
                        _viewModel.resetMediaButtonRecordingSequence()
                    }
                )
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _viewModel.onCapabilityChanged(capabilityInfo)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            // reset self-calibration count and skip calibration activity
            DataSingleton.CALIBRATION_PATH -> {
                val b = ByteBuffer.wrap(messageEvent.data)
                when (b.getInt(20)) {
                    1 -> { // lengthy calibration with vibrating pulse confirmation. Starts a separate activity
                        val i = Intent("com.mocap.phone.CALIBRATION")
                        i.putExtra("sourceNodeId", messageEvent.sourceNodeId)
                        startActivity(i)
                    }
                }
            }
        }
        _viewModel.onMessageReceived(messageEvent)
    }

    /** To register all listeners for all used channels */
    private fun registerListeners() {
        // broadcasts inform about service updates
        val filter = IntentFilter()
        filter.addAction(DataSingleton.BROADCAST_UPDATE)
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(_br, filter)

        _messageClient.addListener(this)
        // checks for a connected device with the Watch capability
        _capabilityClient.addListener(
            this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        // for the watch app to detect this phone
        _capabilityClient.addLocalCapability(DataSingleton.PHONE_APP_ACTIVE)
        // check if a phone is connected and set state flows accordingly
        _viewModel.queryCapabilities()

        // start the media session if listening for buttons but not initialized yet
        if (DataSingleton.listenToMediaButtons.value
            and DataSingleton.recordLocally.value
        ) {
            _mediaSession = MediaSession(this, TAG)
            _mediaSession.setCallback(_callback)
            _mediaSession.setActive(true)
            _viewModel.resetMediaButtonRecordingSequence()
            Log.d(TAG, "ONRESUME - Media Session Active")
        } else if (this::_mediaSession.isInitialized) {
            _mediaSession.setActive(false)
            _mediaSession.release()
            Log.d(TAG, "ONRESUME Media Session Inactive")
        }


        val imuIntent = Intent(this, ImuService::class.java)
        this.startService(imuIntent)
        val ppgIntent = Intent(this, PpgService::class.java)
        this.startService(ppgIntent)
        val audioIntent = Intent(this, AudioService::class.java)
        this.startService(audioIntent)
    }

    override fun onResume() {
        super.onResume()
        registerListeners()
    }

    override fun onPause() {
        super.onPause()

        // Clear all listeners
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
        _messageClient.removeListener(this)
        _capabilityClient.removeListener(this)
        _capabilityClient.removeLocalCapability(DataSingleton.PHONE_APP_ACTIVE)

        val imuIntent = Intent(this, ImuService::class.java)
        this.stopService(imuIntent)
        val ppgIntent = Intent(this, PpgService::class.java)
        this.stopService(ppgIntent)
        val audioIntent = Intent(this, AudioService::class.java)
        this.stopService(audioIntent)

        if (this::_mediaSession.isInitialized) {
            _mediaSession.setActive(false)
            _mediaSession.release()
            Log.d(TAG, "ONPAUSE - Media Session Inactive")
        }
    }
}