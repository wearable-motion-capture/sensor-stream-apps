package com.mocap.phone.modules

import android.util.Log
import com.google.android.gms.wearable.ChannelClient


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
     * Simply forwards it to the viewmodel callback
     */
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        Log.d(TAG, "${channel.path} opened ${channel.nodeId}")
        _openCallback(channel)
    }


    /**
     * Called when a channel is closed.
     * Simply forwards it to the viewmodel callback
     */
    override fun onChannelClosed(
        channel: ChannelClient.Channel,
        closeReason: Int,
        appSpecificErrorCode: Int
    ) {
        Log.d(TAG, "${channel.path} closed ${channel.nodeId}")
        _closeCallback(channel)
    }
}