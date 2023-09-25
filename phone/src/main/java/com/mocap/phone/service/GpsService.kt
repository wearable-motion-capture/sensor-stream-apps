package com.mocap.phone.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.compose.runtime.Composable
import com.mocap.phone.ui.view.LocationUpdatesContent
import com.mocap.phone.ui.view.PermissionBox

class GpsService: Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}


















