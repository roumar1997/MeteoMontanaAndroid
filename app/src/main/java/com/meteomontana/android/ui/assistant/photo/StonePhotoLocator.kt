package com.meteomontana.android.ui.assistant.photo

import android.content.Context
import android.net.Uri
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.ui.components.readPhotoLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Una foto para identificar: hecha ahora con la cámara o elegida de la galería. */
data class StonePhotoInput(val uri: Uri, val fromCamera: Boolean)

/** Dónde se hizo la foto. [approximate] = no lo dice la foto: es donde está el usuario ahora. */
data class StonePhotoPlace(val lat: Double?, val lon: Double?, val approximate: Boolean)

/**
 * Averigua DÓNDE se hizo una foto: con la cámara, en este momento y en este sitio; de la galería, lo que guarde su EXIF
 * (muchas llegan sin él) y, si no, donde está el usuario ahora. Espejo de lo que `SchoolPhotoFinder.swift` y
 * `AssistantViewModel.swift` hacen en iOS. Lo usan el chat del asistente y el botón de cámara de la ficha de escuela.
 */
@Singleton
class StonePhotoLocator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationProvider: LocationProvider
) {
    suspend fun locate(photo: StonePhotoInput): StonePhotoPlace {
        if (photo.fromCamera) {
            val here = currentLocation()
            return StonePhotoPlace(here?.first, here?.second, approximate = false)
        }
        val exif = withContext(Dispatchers.IO) { readPhotoLocation(context, photo.uri) }
        if (exif != null) return StonePhotoPlace(exif.lat, exif.lon, approximate = false)
        val here = currentLocation()
        return StonePhotoPlace(here?.first, here?.second, approximate = here != null)
    }

    private suspend fun currentLocation(): Pair<Double, Double>? {
        if (!locationProvider.hasPermission()) return null
        val here = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { runCatching { locationProvider.current() }.getOrNull() }
        return here?.let { it.lat to it.lon }
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
