package com.meteomontana.android.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.domain.port.LocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Implementación Android de [LocationProvider] (interfaz en shared/commonMain)
 * usando FusedLocation de Google Play Services. iOS tendrá la suya con
 * CLLocationManager. El scope (singleton) lo da el @Provides de LocalModule.
 */
class AndroidLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationProvider {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /** Última ubicación conocida o null si no hay/sin permiso. */
    @SuppressLint("MissingPermission")
    override suspend fun current(): UserLocation? {
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
