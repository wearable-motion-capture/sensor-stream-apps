package com.mocap.phone.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mocap.phone.ui.DefaultHeadline
import com.mocap.phone.ui.BigCard

import android.Manifest
import android.annotation.SuppressLint
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mocap.phone.DataSingleton.setGpsLong


@Composable
fun RenderGpsUpdates(){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DefaultHeadline(text = "Gps Updates")

        BigCard {
            Text(
                text = "Does it work?",
                color = Color.White
            )
            LocationUpdatesScreen()

        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun LocationUpdatesScreen() {
    val permissions = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    // Requires at least coarse permission
    PermissionBox(
        permissions = permissions,
        requiredPermissions = listOf(permissions.first()),
    ) {
        LocationUpdatesContent(
            usePreciseLocation = it.contains(Manifest.permission.ACCESS_FINE_LOCATION),
        )
    }
}

@RequiresPermission(
    anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION],
)
@Composable
fun LocationUpdatesContent(usePreciseLocation: Boolean) {
    // The location request that defines the location updates
    var locationRequest by remember {
        mutableStateOf<LocationRequest?>(null)
    }
    // Keeps track of received location updates as text
    var locationUpdates by remember {
        mutableStateOf("")
    }

    // Only register the location updates effect when we have a request
    if (locationRequest != null) {
        LocationUpdatesEffect(locationRequest!!) { result ->
            // For each result update the text
            locationUpdates =
                result.lastLocation!!.longitude.toString() //latitude locations.listIterator(1).toString()

            locationUpdates = "Time(ms): ${System.currentTimeMillis()}:\n" +
                    "- @lat: ${result.lastLocation!!.latitude}\n" +
                    "- @lng: ${result.lastLocation!!.longitude}\n" +
                    "- @Acc: ${result.lastLocation!!.accuracy}\n" +
                    "- @Alt: ${result.lastLocation!!.altitude}\n" +
                    "- @Spd: ${result.lastLocation!!.speed}\n" +            // Returns the speed at the time of this location in meters per second. Note that the speed returned here may be more accurate than would be obtained simply by calculating distance / time for sequential positions, such as if the Doppler measurements from GNSS satellites are taken into account.
                    "- @Bea: ${result.lastLocation!!.bearing}\n"            // Returns the bearing at the time of this location in degrees. Bearing is the horizontal direction of travel of this device and is unrelated to the device orientation. The bearing is guaranteed to be in the range [0, 360).

            setGpsLong(result.lastLocation!!.longitude.toFloat())
//            for (currentLocation in result.locations) {
//                locationUpdates = "Time(ms): ${System.currentTimeMillis()}:\n" +
//                        "- @lat: ${currentLocation.latitude}\n" +
//                        "- @lng: ${currentLocation.longitude}\n" +
//                        "- @Acc: ${currentLocation.accuracy}\n" +
//                        "- @Speed Acc: ${currentLocation.speedAccuracyMetersPerSecond}\n\n" +
//                        locationUpdates
//            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // Toggle to start and stop location updates
            // before asking for periodic location updates,
            // it's good practice to fetch the current location
            // or get the last known location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Enable location updates")
                Spacer(modifier = Modifier.padding(8.dp))
                Switch(
                    checked = locationRequest != null,
                    onCheckedChange = { checked ->
                        locationRequest = if (checked) {
                            // Define the accuracy based on your needs and granted permissions
                            val priority = if (usePreciseLocation) {
                                Priority.PRIORITY_HIGH_ACCURACY
                            } else {
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY
                            }
//                            LocationRequest.Builder(priority, TimeUnit.SECONDS.toMillis(1)).build()
//                            LocationRequest.Builder(priority, TimeUnit.NANOSECONDS.toNanos(1)).build()
                            LocationRequest.Builder(priority, 1).build()
                        } else {
                            null
                        }
                    },
                )
            }
        }
        item {
            Text(
                text = locationUpdates,
                color = Color.White
            )
        }
    }
}

/**
 * An effect that request location updates based on the provided request and ensures that the
 * updates are added and removed whenever the composable enters or exists the composition.
 */
@RequiresPermission(
    anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION],
)
@Composable
fun LocationUpdatesEffect(
    locationRequest: LocationRequest,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onUpdate: (result: LocationResult) -> Unit,
) {
    val context = LocalContext.current
    val currentOnUpdate by rememberUpdatedState(newValue = onUpdate)

    // Whenever on of these parameters changes, dispose and restart the effect.
    DisposableEffect(locationRequest, lifecycleOwner) {
        val locationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationCallback: LocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                currentOnUpdate(result)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                locationClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.getMainLooper(),
                )
            } else if (event == Lifecycle.Event.ON_STOP) {
                locationClient.removeLocationUpdates(locationCallback)
            }
        }

        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer
        onDispose {
            locationClient.removeLocationUpdates(locationCallback)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

