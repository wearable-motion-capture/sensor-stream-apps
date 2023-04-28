package com.mocap.phone.modules

import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.mocap.phone.DataSingleton

/**
 * This is a forwarder registered with the channel client.
 * The intention is to forward callbacks to the viewmodel.
 */
class PhoneChannelCallback(
    openCallback: (ChannelClient.Channel) -> Unit,
    closeCallback: (ChannelClient.Channel) -> Unit
) : ChannelClient.ChannelCallback() {

    private val _openCallback = openCallback
    private val _closeCallback = closeCallback

    companion object {
        private const val TAG = "ChannelCallback"
    }

    /**
     * Called when a channel is opened.
     * Simply forwards it to the input callback
     */
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path == DataSingleton.CHANNEL_PATH) {
            Log.d(TAG, "channel opened ${channel.nodeId}}")
            _openCallback(channel)
        }
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