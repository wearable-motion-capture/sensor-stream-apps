package com.mocap.watch.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo


enum class ChannelState {
    Idle,
    Streaming,
    Error
}


class ChannelViewModel(application: Application) :
    AndroidViewModel(application),
    CapabilityClient.OnCapabilityChangedListener {

    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private val _nodeClient by lazy { Wearable.getNodeClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private val _channelStateFlow = MutableStateFlow(ChannelState.Idle)
    val channelStateFlow = _channelStateFlow.asStateFlow()


    companion object {
        private const val TAG = "ChannelViewModel"  // for logging
    }

    override fun onCleared() {
        _scope.cancel()
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {

    }
}