package com.mocap.phone.modules

import android.content.Intent
import android.media.session.MediaSession.Callback
import android.util.Log
import android.view.KeyEvent


class MediaSessionButtonsCallback(
    onTrigger: () -> Unit
) : Callback() {
    companion object {
        private const val TAG = "MediaButtonCallback"
    }

    private val _onTriggerFun = onTrigger

    override fun onMediaButtonEvent(intent: Intent): Boolean {
        Log.d(TAG, "received ${intent.action} URI: ${intent.toUri(Intent.URI_INTENT_SCHEME)}")
        // filter MEDIA_BUTTON intents
        if (intent.action == "android.intent.action.MEDIA_BUTTON") {
            val extras = intent.extras
            if (extras != null) {
                // The get method from the extras bundle is deprecated but we kept it to be compatible with older Android versions
                // shape: android.intent.extra.KEY_EVENT : KeyEvent { action=ACTION_UP, keyCode=KEYCODE_MEDIA_PLAY}
                val ke = extras.get("android.intent.extra.KEY_EVENT") as KeyEvent
                if (ke.action == KeyEvent.ACTION_DOWN) {
                    // trigger on on KEY_DOWN
                    _onTriggerFun()
                }
            }
        }
        return true
    }
}