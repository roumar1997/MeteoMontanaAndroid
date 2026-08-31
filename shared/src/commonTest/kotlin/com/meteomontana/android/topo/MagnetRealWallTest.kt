package com.meteomontana.android.topo

import com.meteomontana.android.domain.util.magnetizeStroke
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * El caso REAL que Rodrigo mandó en capturas: el muro de Teverga, 13 vías sobre
 * la misma foto, dibujado con pocos puntos. Al intentar unirse a mitad de una
 * vía, el trazo se iba "donde quería".
 *
 * Aquí está su vía 1 tal cual está guardada en producción. Su tramo más largo
 * mide 0,14 de la foto: tocando en mitad, el vértice más cercano está a 0,072 —
 * un 7% del ancho de la foto, o sea lejísimos en pantalla. Ese salto es
 * exactamente lo que se veía.
 */
class MagnetRealWallTest {

    /** "Hasta aquí", copiada de la piedra real de Teverga. */
    private val viaReal = listOf(
        0.0154f to 0.9075f,
        0.0099f to 0.8811f,
        0.0244f to 0.8559f,
        0.0271f to 0.8157f,
        0.0126f to 0.7274f,
        0.0117f to 0.5828f,
        0.0180f to 0.4611f,
        0.0334f to 0.3350f
    )

    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>) =
        sqrt((a.first - b.first) * (a.first - b.first) + (a.second - b.second) * (a.second - b.second))

    @Test
    fun tocandoAMitadDelTramoLargoElTrazoSeQuedaAhi() {
        // Punto medio del tramo más largo, a 0,072 de los dos vértices.
        val toque = 0.0122f to 0.6551f
        val salida = magnetizeStroke(listOf(toque), listOf(viaReal))
        val donde = salida.first()

        // Antes acababa en un vértice a 0,072 de distancia; ahora debe caer
        // prácticamente donde el dedo dijo.
        assertTrue(dist(donde, toque) < 0.005f,
            "el trazo se fue a $donde cuando el dedo apuntaba a $toque")
    }

    @Test
    fun tocandoJuntoAUnVerticeSiSePegaAEl() {
        // A 0,004 del vértice: aquí SÍ interesa clavarlo exacto, porque es lo
        // que hace que el tramo se comparta y salgan las franjas.
        val vertice = viaReal[4]
        val toque = (vertice.first + 0.004f) to (vertice.second + 0.002f)
        val salida = magnetizeStroke(listOf(toque), listOf(viaReal))
        assertTrue(salida.first() == vertice,
            "esperaba clavarse en $vertice y salió ${salida.first()}")
    }

    @Test
    fun lejosDeLaViaNoSeImantaNada() {
        // A mitad de la foto no hay nada que imantar: el punto se queda igual.
        val toque = 0.5f to 0.5f
        val salida = magnetizeStroke(listOf(toque), listOf(viaReal))
        assertTrue(abs(salida.first().first - 0.5f) < 0.0001f &&
                   abs(salida.first().second - 0.5f) < 0.0001f,
            "no debería tocarse: ${salida.first()}")
    }
}
