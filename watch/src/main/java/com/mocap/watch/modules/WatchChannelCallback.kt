package com.mocap.watch.modules

import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.mocap.watch.DataSingleton

/**
 * This is a forwarder registered with the channel client.
 * The intention is to forward callbacks to the viewmodel.
 */
class WatchChannelCallback(
    closeCallback: (ChannelClient.Channel) -> Unit
) : ChannelClient.ChannelCallback() {

    private val _closeCallback = closeCallback

    companion object {
        private const val TAG = "WatchChannelCallback"
    }

    /**
     * Called when a channel is closed.
     * Simply forwards it to the input callback
     */
    override fun onChannelClosed(
        channel: ChannelClient.Channel,
        closeReason: Int,
        appSpecificErrorCode: Int
    ) {
        if (channel.path == DataSingleton.CHANNEL_PATH) {
            Log.d(TAG, "channel closed ${channel.nodeId}}")
            _closeCallback(channel)
        }
    }
}