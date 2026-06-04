package com.meteomontana.android.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class UserLocation(val lat: Double, val lon: Double)

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /** Devuelve la última ubicación conocida o null si no hay/sin permiso. */
    @SuppressLint("MissingPermission")
    suspend fun current(): UserLocation? {
        if (!hasPermission()) return null
        return try {
            // currentLocation devuelve null si nunca ha habido ubicación; intentamos getLastLocation
            val last = fused.lastLocation.await()
            if (last != null) UserLocation(last.latitude, last.longitude)
            else {
                val fresh = fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                fresh?.let { UserLocation(it.latitude, it.longitude) }
            }
        } catch (_: Throwable) { null }
    }
}
