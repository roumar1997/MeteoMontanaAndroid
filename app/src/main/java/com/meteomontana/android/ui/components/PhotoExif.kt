package com.meteomontana.android.ui.components

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
 * Lee las coordenadas de la foto.
 *
 * En Android 10 y posteriores el sistema **borra la ubicación** de la copia que
 * te entrega salvo que pidas el original con `setRequireOriginal`, y eso exige
 * el permiso `ACCESS_MEDIA_LOCATION`. Sin ese paso, esta función devolvería
 * null en fotos que sí tienen coordenadas.
 */
fun readPhotoLocation(context: Context, uri: Uri): PhotoLocation? {
    val enMediaStore = aMediaStore(uri)
    // Rastro para diagnosticar en dispositivo: los selectores de fotos entregan
    // COPIAS distintas segun de donde vengan, y hasta no ver de cual se trata no
    // se puede saber por que una foto con coordenadas llega sin ellas.
    android.util.Log.i(TAG, "uri=$uri authority=${uri.authority} -> media=$enMediaStore")

    // 1) El original de MediaStore (el unico con la ubicacion intacta).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val original = runCatching { MediaStore.setRequireOriginal(enMediaStore) }.getOrNull()
        if (original != null) {
            leer(context, original)?.let {
                android.util.Log.i(TAG, "coordenadas del ORIGINAL: ok")
                return it
            }
            android.util.Log.i(TAG, "el original no dio coordenadas")
        }
    }
    // 2) El FICHERO, por su ruta. Es el camino que no pasa por el recorte del
    //    proveedor: desde Android 11, con permiso de galeria, se puede leer el
    //    archivo tal cual esta en disco. Es lo que salva el caso en el que el
    //    permiso figura concedido pero el sistema sigue devolviendo una copia
    //    sin ubicacion (visto en el Xiaomi de Rodrigo).
    rutaDe(context, enMediaStore)?.let { ruta ->
        runCatching {
            val exif = ExifInterface(ruta)
            val coords = exif.latLong
            if (coords != null) {
                android.util.Log.i(TAG, "coordenadas del FICHERO: ok")
                return PhotoLocation(coords[0], coords[1],
                    exif.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION)
                        ?.let { fraccionADecimal(it) })
            }
        }.onFailure { android.util.Log.i(TAG, "fallo leyendo el fichero $ruta: $it") }
    }

    // 3) Lo que haya llegado, tal cual. Puede bastar si el proveedor no redacta.
    leer(context, uri)?.let {
        android.util.Log.i(TAG, "coordenadas de la copia: ok")
        return it
    }
    android.util.Log.i(TAG, "sin coordenadas por ningun camino")
    return null
}

/** Ruta en disco de una foto de la galeria, si el sistema la expone. */
private fun rutaDe(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(
        uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null
    )?.use { c ->
        if (c.moveToFirst()) c.getString(0)?.takeIf { java.io.File(it).canRead() } else null
    }
}.getOrNull()

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
 * Traduce la URI a una de MediaStore, que es la unica sobre la que se puede
 * pedir el fichero ORIGINAL (con su ubicacion).
 *
 * Si ya lo es, se devuelve tal cual. Si viene del proveedor de documentos
 * ("image:1234"), se reconstruye. Cualquier otra cosa se deja pasar: se leera
 * lo que haya, que es mejor que no leer nada.
 */
private fun aMediaStore(uri: Uri): Uri {
    if (uri.authority == "media") return uri
    val id = uri.lastPathSegment?.substringAfterLast(':')?.toLongOrNull() ?: return uri
    return android.content.ContentUris.withAppendedId(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
}

/**
 * El EXIF guarda el rumbo como fracción ("2700/100"). También se acepta un
 * número suelto, que es como lo escriben algunas cámaras.
 */
private fun fraccionADecimal(raw: String): Float? {
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
