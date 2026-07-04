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

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Ubicación actual o null si no hay/sin permiso.
     *
     * Antes: lastLocation (podía ser una posición VIEJA de otra app) con
     * BALANCED_POWER (WiFi/antenas, 100 m–1 km, sin encender el GPS) y solo
     * permiso COARSE → en el monte el punto azul caía a 500 m–1 km (visto en
     * escuela el 2026-07-04). Ahora: lastLocation solo si es reciente (<60 s)
     * y precisa (<100 m); si no, fix fresco con GPS de alta precisión.
     */
    @SuppressLint("MissingPermission")
    override suspend fun current(): UserLocation? {
        if (!hasPermission()) return null
        return try {
            val last = fused.lastLocation.await()
            val freshEnough = last != null &&
                    (System.currentTimeMillis() - last.time) < 60_000 &&
                    last.accuracy <= 100f
            if (freshEnough) return UserLocation(last!!.latitude, last.longitude)

            val priority = if (hasFine()) Priority.PRIORITY_HIGH_ACCURACY
                           else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val fresh = fused.getCurrentLocation(priority, null).await()
            (fresh ?: last)?.let { UserLocation(it.latitude, it.longitude) }
        } catch (_: Throwable) { null }
    }
}
