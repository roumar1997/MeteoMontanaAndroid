package com.meteomontana.android.domain.port

import com.meteomontana.android.domain.model.UserLocation

/**
 * Ubicación del usuario. Interfaz compartida (KMP); cada plataforma la
 * implementa: Android con FusedLocation, iOS con CLLocationManager.
 */
interface LocationProvider {

    /** ¿Tenemos permiso de ubicación concedido? */
    fun hasPermission(): Boolean

    /** Última ubicación conocida, o null si no hay/sin permiso. */
    suspend fun current(): UserLocation?
}
