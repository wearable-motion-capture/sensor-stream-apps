package com.mocap.watch.service.udp

import android.content.Intent
import android.util.Log
import com.mocap.watch.DataSingleton
import com.mocap.watch.service.BaseImuService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer


class UdpImuService : BaseImuService() {

    companion object {
        private const val TAG = "UDP IMU Service"  // for logging
    }

    /**
     * Triggers the streaming of IMU data as a service
     * or (if already running) stops the service.
     */
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // check if a source node ID was sent with the application
        if (imuStreamState) {
            Log.w(TAG, "stream already started")
            onDestroy() // stop service
        } else {
            imuStreamState = true
            scope.launch { susStreamData() }
        }
        return START_NOT_STICKY
    }

    /**
     * Stream IMU data in a while loop.
     * This function can be suspended to be able to kill the loop by stopping or pausing
     * the scope it was started from.
     */
    private suspend fun susStreamData() {

        val ip = DataSingleton.ip.value
        val port = DataSingleton.UDP_IMU_PORT

        withContext(Dispatchers.IO) {
            try {
                // open a socket
                val udpSocket = DatagramSocket(port)
                udpSocket.broadcast = true
                val socketInetAddress = InetAddress.getByName(ip)
                Log.v(TAG, "Opened UDP socket to $ip:$port")

                udpSocket.use {

                    // register all listeners with their assigned codes
                    registerSensorListeners()

                    // start the stream loop
                    while (imuStreamState) {
                        // compose message
                        val lastDat = composeImuMessage()
                        // only process if a message was composed successfully
                        if (lastDat != null) {
                            // feed into byte buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.IMU_MSG_SIZE)
                            for (v in lastDat) buffer.putFloat(v)
                            val dp = DatagramPacket(
                                buffer.array(), buffer.capacity(), socketInetAddress, port
                            )
                            udpSocket.send(dp)  // finally, send the byte stream
                        }
                        // avoid sending too fast. Delay coroutine for milliseconds
                        delay(MSGBREAK)
                    }
                }
            } catch (e: Exception) {
                // if the loop ends of was disrupted, stop the service and
                // simply wait for its destruction
                Log.w(TAG, e)
                stopService()
            }
        }
    }
}