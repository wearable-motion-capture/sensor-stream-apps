package com.mocap.watch.service.channel

import android.app.Service
import android.content.Intent
import android.hardware.SensorEvent
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
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue


class ChannelPpgService : Service() {

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

    // the onHrRawReadout will fill this queue with measurements.
    // the streamTrigger Coroutine will channel them to the phone
    private var _ppgQueue = ConcurrentLinkedQueue<FloatArray>()

    override fun onCreate() {
        // access and observe sensors
        _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private fun streamTrigger(nodeId: String) {
        if (_ppgStreamState) {
            Log.w(TAG, "stream already started")
            onDestroy() // stop service
            return
        }
        _scope.launch {
            try {
                // Open the channel
                val channel = _channelClient.openChannel(
                    nodeId,
                    DataSingleton.PPG_PATH
                ).await()
                Log.d(TAG, "Opened ${DataSingleton.IMU_PATH} to $nodeId")

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

                                // get time stamp as float array to ease parsing
                                val dt = LocalDateTime.now()
                                val ts = floatArrayOf(
                                    dt.hour.toFloat(),
                                    dt.minute.toFloat(),
                                    dt.second.toFloat(),
                                    dt.nano.toFloat()
                                )

                                val buffer = ByteBuffer.allocate(DataSingleton.PPG_MSG_SIZE)

                                // feed into byte buffer
                                for (v in (ts + lastDat)) buffer.putFloat(v)

                                // write to output stream
                                outputStream.write(buffer.array(), 0, buffer.capacity())
                            }
                        }
                    }
                } catch (e: Exception) {
                    // In case the channel gets destroyed while still in the loop
                    Log.d(TAG, e.toString())
                } finally {
                    _ppgStreamState = false
                    _sensorManager.unregisterListener(_ppgListener)     // remove listener again
                    Log.d(TAG, "PPG stream stopped")
                    _channelClient.close(channel)
                    onDestroy() // stop service
                }
            } catch (e: Exception) {
                // In case the channel gets destroyed while still in the loop
                Log.w(TAG, "Channel failed" + e.message.toString())
                onDestroy() // stop service
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        val sourceId = intent.extras?.getString("sourceNodeId")
        if (sourceId == null) {
            Log.w(TAG, "no Node ID given")
            onDestroy() // stop service
            return START_NOT_STICKY
        }
        streamTrigger(sourceId)
        return START_NOT_STICKY
    }

    private fun onHrRawReadout(newReadout: SensorEvent) {
        _ppgQueue.add(newReadout.values)
    }

    override fun onDestroy() {
        super.onDestroy()
        _ppgStreamState = false
        _scope.cancel()
        if (this::_sensorManager.isInitialized) {
            _sensorManager.unregisterListener(_ppgListener)
        }
        val intent = Intent(DataSingleton.BROADCAST_CLOSE)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.PPG_PATH)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    /** not intended to be bound **/
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}