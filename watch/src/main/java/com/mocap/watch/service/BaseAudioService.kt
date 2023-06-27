package com.mocap.watch.service

import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mocap.watch.DataSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel


abstract class BaseAudioService : Service() {

    companion object {
        private const val TAG = "Audio Service"  // for logging
        const val AUDIO_RATE = 16000 // can go up to 44K, if needed
        const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val AUDIO_BUFFER_SIZE = 1600
    }

    protected val scope = CoroutineScope(Job() + Dispatchers.IO)
    protected var audioStreamState = false

    override fun onDestroy() {
        super.onDestroy()
        audioStreamState = false
        val intent = Intent(DataSingleton.BROADCAST_CLOSE)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.AUDIO_UDP_PATH)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        scope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    /** not intended to be bound **/
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}