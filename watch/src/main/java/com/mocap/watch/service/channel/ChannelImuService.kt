package com.mocap.watch.service.channel
import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.service.BaseImuService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer


class ChannelImuService : BaseImuService() {

    companion object {
        private const val TAG = "Channel IMU Service"  // for logging
    }

    private val _channelClient by lazy { Wearable.getChannelClient(application) }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        val sourceId = intent.extras?.getString("sourceNodeId")
        if (sourceId == null) {
            Log.w(TAG, "no Node ID given")
            onDestroy()
        } else if (imuStreamState) {
            Log.w(TAG, "stream already started")
            onDestroy()
        } else {
            imuStreamState = true
            scope.launch { susStreamTrigger(sourceId) }
        }
        return START_NOT_STICKY
    }

    private suspend fun susStreamTrigger(nodeId: String) {

        try {
            // Open the channel
            val channel = _channelClient.openChannel(
                nodeId,
                DataSingleton.IMU_PATH
            ).await()
            Log.d(TAG, "Opened ${DataSingleton.IMU_PATH} to $nodeId")

            try {
                // get output stream
                val outputStream = _channelClient.getOutputStream(channel).await()
                // get output stream
                outputStream.use {

                    // register all listeners with their assigned codes
                    registerSensorListeners()

                    // start the stream loop
                    while (imuStreamState) {
                        // compose message
                        val lastDat = composeImuMessage()
                        if (lastDat != null) {
                            // only process if a message was composed successfully
                            // feed into byte buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.IMU_MSG_SIZE)
                            for (v in lastDat) buffer.putFloat(v)
                            // write to output stream
                            outputStream.write(buffer.array())
                        }
                        // avoid sending too fast. Delay coroutine for milliseconds
                        delay(MSGBREAK)
                    }
                }
            } catch (e: Exception) {
                // In case the channel gets destroyed while still in the loop
                Log.w(TAG, e)
            } finally {
                _channelClient.close(channel)
                stopService()
            }
        } catch (e: Exception) {
            // In case the channel gets destroyed/closed while still in the loop
            Log.w(TAG, e)
            stopService()
        }
    }
}