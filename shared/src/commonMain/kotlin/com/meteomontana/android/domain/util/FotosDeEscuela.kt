package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Block

/**
 * Qué fotos hay que tener en el móvil para ver una escuela SIN cobertura.
 *
 * Vive en la capa compartida porque la regla es la misma en Android y en iOS, y
 * porque es justo donde estaba el fallo: las dos apps pre-descargaban solo la
 * PORTADA de cada piedra (`Block.photoPath`) y se dejaban las fotos de las CARAS
 * (`BlockLine.photoPath`). Una piedra de tres caras se quedaba con una foto y
 * dos huecos en la roca, que es cuando no hay forma de arreglarlo.
 *
 * Es una función pura a propósito: descargar y escribir ficheros lo hace cada
 * plataforma, pero *qué* descargar se decide una sola vez y se puede probar.
 */
object FotosDeEscuela {

    /**
     * URLs a guardar, sin repetir y en orden estable.
     *
     * Sin repetir importa de verdad: cuando una piedra tiene una sola cara, la
     * vía hereda la foto de la piedra, así que la misma URL sale por los dos
     * lados y se descargaría dos veces.
     */
    fun urlsParaGuardar(blocks: List<Block>): List<String> =
        blocks
            .flatMap { bloque ->
                listOf(bloque.photoPath) + bloque.lines.map { it.photoPath }
            }
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()

    /**
     * Peso aproximado en bytes de [urls], para poder preguntar al usuario antes
     * de tirar de sus datos.
     *
     * Es una ESTIMACIÓN: el tamaño real solo se sabe al descargar. Los 280 KB
     * salen de medir las fotos de producción tras reducirlas a 1600px/q75
     * (2026-08-16): 165 fotos ocupaban 46 MB.
     */
    fun pesoEstimadoBytes(urls: List<String>): Long = urls.size * BYTES_POR_FOTO

    private const val BYTES_POR_FOTO = 280L * 1024
}
