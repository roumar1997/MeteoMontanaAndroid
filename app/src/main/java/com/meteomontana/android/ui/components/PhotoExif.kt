package com.meteomontana.android.ui.components

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Dónde se hizo una foto, leído de su EXIF.
 *
 * Es lo que permite proponer una piedra sin buscar la escuela a mano: la cámara
 * guarda las coordenadas dentro del fichero.
 *
 * **Muchas fotos NO lo traen** y es normal: si llegó por WhatsApp, si se
 * descargó de internet, o si tenías la ubicación desactivada en la cámara, el
 * dato no está. Por eso todo aquí devuelve null sin dramatizar y quien llama
 * enseña el aviso.
 */
data class PhotoLocation(
    val lat: Double,
    val lon: Double,
    /**
     * Hacia dónde apuntaba la cámara al disparar, si la foto lo trae. Sirve para
     * sugerir la orientación de la pared (que mira justo al revés). La mayoría
     * de fotos no lo tienen.
     */
    val cameraDegrees: Float?
)

/**
 * Lee las coordenadas de la foto que ha entregado el selector del sistema.
 *
 * Hubo aquí tres caminos: pedir el original a MediaStore, leer el fichero por
 * su ruta en disco, y finalmente leer la copia tal cual. Los dos primeros
 * necesitaban permiso de galería y `ACCESS_MEDIA_LOCATION`, que ya no se
 * declaran (ver AndroidManifest.xml: Google Play los prohíbe para este uso), y
 * sin permiso solo lanzaban una excepción que se tragaba el `runCatching`. Se
 * quedan fuera: código muerto que aparentaba cubrir casos que no cubría.
 *
 * Queda leer lo que el proveedor haya dado. **Muchas fotos llegarán sin
 * coordenadas** —cada selector y cada fabricante recorta lo suyo— y por eso
 * quien llama tiene siempre preparada la vía de colocar el punto a mano.
 */
fun readPhotoLocation(context: Context, uri: Uri): PhotoLocation? {
    val donde = leer(context, uri)
    android.util.Log.i(TAG, "uri=$uri authority=${uri.authority} -> coords=${donde != null}")
    return donde
}

private const val TAG = "CumbreFoto"

private fun leer(context: Context, uri: Uri): PhotoLocation? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val exif = ExifInterface(input)
        val coords = exif.latLong ?: return@use null
        val rumbo = exif.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION)
            ?.let { fraccionADecimal(it) }
        PhotoLocation(coords[0], coords[1], rumbo)
    }
}.onFailure { android.util.Log.i(TAG, "fallo leyendo $uri: $it") }.getOrNull()

/**
 * El EXIF guarda el rumbo como fracción ("2700/100"). También se acepta un
 * número suelto, que es como lo escriben algunas cámaras.
 */
internal fun fraccionADecimal(raw: String): Float? {
    val partes = raw.split("/")
    return when {
        partes.size == 2 -> {
            val n = partes[0].toFloatOrNull() ?: return null
            val d = partes[1].toFloatOrNull() ?: return null
            if (d == 0f) null else n / d
        }
        else -> raw.toFloatOrNull()
    }
}
