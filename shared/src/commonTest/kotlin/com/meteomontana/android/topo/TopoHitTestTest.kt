package com.meteomontana.android.topo

import com.meteomontana.android.domain.util.distanceToPolyline
import com.meteomontana.android.domain.util.nearestLineIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Elegir vía con el dedo en una piedra con muchas. Sin esto, el modo foco
 * sería inútil: acertar la línea correcta entre trece es cuestión de suerte.
 */
class TopoHitTestTest {

    // Dos vías verticales paralelas, separadas 0,2 de ancho.
    private val izquierda = listOf(0.2f to 0.9f, 0.2f to 0.5f, 0.2f to 0.1f)
    private val derecha = listOf(0.4f to 0.9f, 0.4f to 0.5f, 0.4f to 0.1f)
    private val dos = listOf(izquierda, derecha)

    @Test
    fun tocarEnMitadDeUnaViaLaSelecciona() {
        // Lo importante: se mide contra el TRAMO, no contra los vértices. Si
        // fuera contra vértices, tocar a media altura no acertaría nada.
        assertEquals(0, nearestLineIndex(dos, 0.205f, 0.72f))
    }

    @Test
    fun eligeLaMasCercanaCuandoEstanJuntas() {
        assertEquals(1, nearestLineIndex(dos, 0.37f, 0.5f))
        assertEquals(0, nearestLineIndex(dos, 0.23f, 0.5f))
    }

    @Test
    fun tocarLejosNoSeleccionaNada() {
        // Y eso es lo que apaga el foco al tocar la roca vacía.
        assertNull(nearestLineIndex(dos, 0.85f, 0.5f))
    }

    @Test
    fun elUmbralSeRespeta() {
        assertNull(nearestLineIndex(dos, 0.2f, 0.5f, maxDistance = 0f))
        assertEquals(0, nearestLineIndex(dos, 0.24f, 0.5f, maxDistance = 0.05f))
    }

    @Test
    fun unaViaDeUnSoloPuntoTambienSePuedeTocar() {
        // Pasa mientras se está dibujando: el primer punto ya debe ser tocable.
        val suelta = listOf(listOf(0.5f to 0.5f))
        assertEquals(0, nearestLineIndex(suelta, 0.51f, 0.51f))
    }

    @Test
    fun lasViasVaciasSeIgnoran() {
        val conVacia = listOf(emptyList<Pair<Float, Float>>(), izquierda)
        assertEquals(1, nearestLineIndex(conVacia, 0.2f, 0.5f))
    }

    @Test
    fun laDistanciaEsLaGeometricaDeVerdad() {
        // Un punto a 0,1 de una vía vertical está a 0,1 — ni más ni menos.
        val d = distanceToPolyline(0.3f, 0.5f, izquierda)
        assertTrue(kotlin.math.abs(d - 0.1f) < 0.001f, "esperaba 0,1 y salió $d")
    }

    @Test
    fun sinViasNoRevienta() {
        assertNull(nearestLineIndex(emptyList(), 0.5f, 0.5f))
        assertEquals(Float.MAX_VALUE, distanceToPolyline(0.5f, 0.5f, emptyList()))
    }
}
