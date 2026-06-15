package com.meteomontana.android.data.location

import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.domain.port.LocationProvider
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Bridge que IMPLEMENTA Swift (con CLLocationManager). Solo callbacks, sin
 * `suspend`: implementar un `suspend` de Kotlin desde Swift no es directo, así
 * que Swift expone una API simple con callback y el lado Kotlin
 * ([IosLocationProvider]) la envuelve en una corrutina.
 *
 * Equivalente iOS del FusedLocation de [AndroidLocationProvider].
 */
interface IosLocationBridge {
    /** ¿Permiso de ubicación concedido? (authorizedWhenInUse/Always). */
    fun hasPermission(): Boolean

    /** Última ubicación conocida; llama al callback con null si no hay/sin permiso. */
    fun current(callback: (UserLocation?) -> Unit)
}

/**
 * Implementación iOS de [LocationProvider]. Convierte el callback del bridge
 * Swift en una función `suspend` usando [suspendCancellableCoroutine], igual
 * que `AndroidLocationProvider` usa `await()` sobre los Task de Play Services.
 */
class IosLocationProvider(
    private val bridge: IosLocationBridge,
) : LocationProvider {

    override fun hasPermission(): Boolean = bridge.hasPermission()

    override suspend fun current(): UserLocation? =
        suspendCancellableCoroutine { cont ->
            bridge.current { location ->
                if (cont.isActive) cont.resume(location)
            }
        }
}
