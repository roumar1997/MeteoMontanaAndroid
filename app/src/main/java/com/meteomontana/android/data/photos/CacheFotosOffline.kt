package com.meteomontana.android.data.photos

import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Copia en disco de las fotos de las escuelas guardadas, para verlas SIN
 * cobertura. Equivalente Android de `ImageCache.swift` (iOS lo tenía desde
 * antes; Android se apoyaba solo en la caché de Coil, que el sistema borra
 * cuando necesita espacio — justo el día que estás en la roca).
 *
 * Vive en `filesDir`, no en `cacheDir`, A PROPÓSITO: lo de `cacheDir` lo puede
 * borrar Android en cualquier momento, y estas fotos las pidió el usuario
 * expresamente al guardar la escuela. Se borran cuando él quita la escuela.
 *
 * Tamaño real medido (2026-08-16): la escuela más pesada del catálogo son ~7 MB.
 */
class CacheFotosOffline(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val log = Logger.withTag("CacheFotos")

    private val carpeta: File
        get() = File(context.filesDir, CARPETA).apply { mkdirs() }

    /**
     * Nombre de fichero ESTABLE a partir de la URL — mismo algoritmo (FNV-1a)
     * que usa iOS, para que las dos apps se comporten igual. `hashCode()` de
     * Java NO vale: no está garantizado entre versiones de la JVM.
     */
    private fun clave(url: String): String {
        var h = -3750763034362895579L   // offset basis de FNV-1a, 64 bits
        for (b in url.encodeToByteArray()) {
            h = h xor (b.toLong() and 0xFF)
            h *= 1099511628211L
        }
        return java.lang.Long.toHexString(h) + ".img"
    }

    private fun ficheroDe(url: String) = File(carpeta, clave(url))

    /** Ruta local de [url] si ya está descargada, o null. */
    fun rutaLocal(url: String): String? = ficheroDe(url).takeIf { it.exists() }?.absolutePath

    /**
     * Descarga las que falten. Idempotente: lo ya bajado se salta, así que
     * volver a llamar tras añadirse piedras nuevas solo trae lo nuevo.
     *
     * @param onProgreso descargadas / total, para poder enseñar el avance.
     * @return cuántas fallaron (0 = todo bien). Una foto que falla NO aborta el
     *   resto: es mejor tener 24 de 25 que ninguna.
     */
    suspend fun descargar(
        urls: List<String>,
        onProgreso: (hechas: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(dispatcher) {
        val pendientes = urls.filter { !ficheroDe(it).exists() }
        var fallos = 0
        pendientes.forEachIndexed { i, url ->
            runCatching {
                val destino = ficheroDe(url)
                val temporal = File(destino.absolutePath + ".parcial")
                // CON timeouts: una red que acepta la conexión y luego no
                // responde (portal wifi, cobertura fantasma) dejaría la descarga
                // colgada para siempre, y con ella el diálogo de progreso.
                val conexion = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }
                try {
                    conexion.inputStream.use { entrada ->
                        temporal.outputStream().use { entrada.copyTo(it) }
                    }
                } finally {
                    conexion.disconnect()
                }
                // Renombrar al final: si se corta la descarga NO queda un
                // fichero a medias haciéndose pasar por la foto buena.
                if (!temporal.renameTo(destino)) {
                    temporal.delete()
                    error("no se pudo cerrar la descarga de $url")
                }
            }.onFailure {
                fallos++
                log.w("Foto offline fallida ($url): ${it.message}")
            }
            onProgreso(i + 1, pendientes.size)
        }
        log.i("Fotos offline: ${pendientes.size - fallos}/${pendientes.size} nuevas, $fallos fallos")
        fallos
    }

    /**
     * Borra las fotos que ya no estén en [urlsQueSiguen]. Se llama tras
     * refrescar una escuela guardada: si se borró una piedra, su foto no tiene
     * por qué seguir ocupando sitio.
     */
    suspend fun limpiarSobrantes(urlsQueSiguen: List<String>) = withContext(dispatcher) {
        val vigentes = urlsQueSiguen.map { clave(it) }.toSet()
        carpeta.listFiles()?.forEach { f ->
            if (f.name !in vigentes) runCatching { f.delete() }
        }
        Unit
    }

    /** Borra las fotos de [urls] (al quitar una escuela de guardadas). */
    suspend fun borrar(urls: List<String>) = withContext(dispatcher) {
        urls.forEach { runCatching { ficheroDe(it).delete() } }
        Unit
    }

    /** Lo que ocupan todas las fotos guardadas, para poder enseñarlo. */
    suspend fun bytesOcupados(): Long = withContext(dispatcher) {
        carpeta.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private companion object {
        const val CARPETA = "fotos-offline"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
