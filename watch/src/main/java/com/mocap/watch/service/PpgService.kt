package com.mocap.watch.service

import android.app.Service
import android.content.Intent
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.Wearable
import com.mocap.watch.DataSingleton
import com.mocap.watch.modules.SensorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue


class PpgService : Service() {

    companion object {
        private const val TAG = "PPG Service"  // for logging
    }

    private lateinit var _sensorManager: SensorManager
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private var _ppgStreamState = false

    // Samsung Galaxy5 HR Raw Sensor
    private val _ppgListener = SensorListener(
        69682
    ) { onHrRawReadout(it) }

    // the sensor listener will fill this queue with measurements.
    // the sensorStreamTrigger Coroutine will channel them to the phone
    private var _ppgQueue = ConcurrentLinkedQueue<FloatArray>()

    override fun onCreate() {
        // access and observe sensors
        _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private fun streamTrigger(nodeId: String) {
        if (_ppgStreamState) {
            Log.w(TAG, "stream already started")
            stopSelf()
            return
        }
        _scope.launch {
            try {
                // Open the channel
                val channel = _channelClient.openChannel(
                    nodeId,
                    DataSingleton.PPG_CHANNEL_PATH
                ).await()
                Log.d(TAG, "Opened ${DataSingleton.IMU_CHANNEL_PATH} to $nodeId")

                try {
                    // get output stream
                    val outputStream = _channelClient.getOutputStream(channel).await()
                    // get output stream
                    outputStream.use {

                        // register the ppg listener
                        _sensorManager.registerListener(
                            _ppgListener,
                            _sensorManager.getDefaultSensor(_ppgListener.code),
                            SensorManager.SENSOR_DELAY_FASTEST
                        )

                        // start the stream loop
                        _ppgStreamState = true
                        while (_ppgStreamState) {

                            // get data from queue
                            // skip entries that are already old. This only happens if the watch
                            // processes incoming measurements too slowly
                            var lastDat = _ppgQueue.poll()
                            while (_ppgQueue.count() > 10) {
                                lastDat = _ppgQueue.poll()
                            }

                            // if we got some data from the watch...
                            if (lastDat != null) {
                                // feed into byte buffer
                                val buffer = ByteBuffer.allocate(4 * DataSingleton.PPG_MSG_SIZE)
                                for (v in lastDat) buffer.putFloat(v)

                                // write to output stream
                                outputStream.write(buffer.array(), 0, buffer.capacity())
                            }
                        }
                    }
                } catch (e: Exception) {
                    // In case the channel gets destroyed while still in the loop
                    Log.d(TAG, e.message.toString())
                } finally {
                    _ppgStreamState = false
                    _sensorManager.unregisterListener(_ppgListener)     // remove listener again
                    Log.d(TAG, "PPG stream stopped")
                    _channelClient.close(channel)
                    stopSelf() // stop service
                }
            } catch (e: Exception) {
                // In case the channel gets destroyed while still in the loop
                Log.w(TAG, "Channel failed" + e.message.toString())
                stopSelf() // stop service
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        val sourceId = intent.extras?.getString("sourceNodeId")
        if (sourceId == null) {
            Log.w(TAG, "no Node ID given")
            stopSelf()
            return START_NOT_STICKY
        }
        streamTrigger(sourceId)
        return START_NOT_STICKY
    }

    private fun onHrRawReadout(newReadout: FloatArray) {
        _ppgQueue.add(newReadout)
    }

    override fun onDestroy() {
        super.onDestroy()
        _ppgStreamState = false
        _scope.cancel()
        if (this::_sensorManager.isInitialized) {
            _sensorManager.unregisterListener(_ppgListener)
        }
        val intent = Intent(DataSingleton.BROADCAST_CLOSE)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.PPG_CHANNEL_PATH)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    /** not intended to be bound **/
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}