package com.meteomontana.android.data.photos

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copias propias de las fotos que elige el usuario.
 *
 * POR QUÉ EXISTE. El selector del sistema (`OpenDocument`) no entrega la foto:
 * entrega una *dirección* con permiso de lectura **temporal**, que vale mientras
 * viva la pantalla que la pidió. En cuanto la app se cierra o el sistema recrea
 * la pantalla, ese permiso desaparece y la dirección deja de poder leerse.
 *
 * Eso rompía dos cosas a la vez, y en SILENCIO:
 *  - el borrador de una piedra guardaba esas direcciones como texto y sobrevivía
 *    a cerrar la app → al volver, las fotos ya no se podían leer;
 *  - al proponer una piedra sin cobertura, la copia se hacía al pulsar "guardar"
 *    (a veces mucho después de elegir la foto) y si fallaba se devolvía null: esa
 *    cara se encolaba SIN foto y el usuario no se enteraba. De ahí "subo tres
 *    fotos y solo aparece una" (reportado por Rodrigo, 2026-08-15).
 *
 * La regla, por tanto: **copiar en cuanto se elige**, mientras el permiso está
 * vivo, y a partir de ahí manejar SIEMPRE nuestro fichero. Un fichero propio no
 * caduca.
 */
object FotosLocales {

    /** Carpeta de las copias, dentro del almacenamiento privado de la app. */
    private const val CARPETA = "fotos-elegidas"

    /**
     * Copia la foto de [origen] a un fichero propio y devuelve su dirección.
     *
     * Devuelve [Result] a propósito, NO null: perder una foto tiene que poder
     * contarse al usuario. Quien llama decide qué hacer, pero no puede ignorarlo
     * sin querer.
     */
    suspend fun copiar(
        context: Context,
        origen: Uri,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Uri> = withContext(dispatcher) {
        runCatching {
            val carpeta = File(context.filesDir, CARPETA).apply { mkdirs() }
            val destino = File(carpeta, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(origen)
                ?.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
                ?: error("No se pudo abrir la foto elegida (¿permiso caducado?)")
            if (destino.length() == 0L) {
                destino.delete()
                error("La foto elegida llegó vacía")
            }
            destino.toUri()
        }
    }

    /** true si [uri] apunta a una copia nuestra que sigue existiendo. */
    fun existe(uri: Uri): Boolean =
        uri.path?.let { File(it).exists() } == true

    /** Borra la copia (tras subirla). Silencioso: que falle no rompe nada. */
    fun borrar(uri: Uri) {
        runCatching { uri.path?.let { File(it).delete() } }
    }
}
