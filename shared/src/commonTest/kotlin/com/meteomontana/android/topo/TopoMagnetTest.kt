package com.meteomontana.android.topo

import com.meteomontana.android.domain.util.TopoMagnet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El interruptor del imán y la marca de "aquí ha enganchado".
 *
 * El caso que lo motiva es el de Rodrigo: dos vías que arrancan casi pegadas
 * pero son independientes abajo, y que sí comparten el tramo del medio. Con el
 * imán siempre activo, la de abajo se pegaba sin remedio.
 */
class TopoMagnetTest {

    /** Una vía vertical con vértices espaciados, como una real. */
    private val otra = listOf(
        0.40f to 0.90f,
        0.40f to 0.70f,
        0.40f to 0.50f,
        0.40f to 0.30f
    )

    @Test
    fun apagadoElTrazoSeRespetaAunquePaseAlLado() {
        // A 0,01 de la otra vía: con el imán encendido se pegaría seguro.
        val trazo = listOf(0.41f to 0.90f, 0.41f to 0.70f)
        val salida = TopoMagnet.apply(trazo, listOf(otra), enabled = false)
        assertEquals(trazo, salida, "con el imán apagado no debe moverse nada")
    }

    @Test
    fun encendidoSePegaComoSiempre() {
        val trazo = listOf(0.41f to 0.90f, 0.41f to 0.70f)
        val salida = TopoMagnet.apply(trazo, listOf(otra), enabled = true)
        assertTrue(salida.any { it in otra },
            "con el imán encendido algún punto debería caer sobre la otra vía: $salida")
    }

    @Test
    fun apagadoNoSeUneAunSiendoElMismoPunto() {
        // Ni siquiera un punto idéntico se "reconoce": apagado es apagado. Lo
        // que sí queda es que el punto YA coincide, y eso lo dirá joinedIndices.
        val trazo = listOf(0.40f to 0.70f)
        assertEquals(trazo, TopoMagnet.apply(trazo, listOf(otra), enabled = false))
    }

    @Test
    fun marcaSoloLosPuntosQueRealmenteCoinciden() {
        val linea = listOf(
            0.30f to 0.95f,        // suelto
            0.40f to 0.70f,        // vértice exacto de `otra`
            0.402f to 0.50f        // MUY cerca, pero no es el mismo punto
        )
        assertEquals(setOf(1), TopoMagnet.joinedIndices(linea, listOf(otra)),
            "solo el punto copiado del vértice cuenta como unido")
    }

    @Test
    fun encenderElImanNoUneLoQueYaHabiasDibujado() {
        // El fallo que Rodrigo vio en el Xiaomi: dibujó el arranque pegado a
        // otra vía con el imán APAGADO, lo encendió para el tramo del medio, y
        // se unió también el arranque, que ya había decidido dejar suelto.
        var via = listOf<Pair<Float, Float>>()
        // Dos puntos con el imán apagado, a 0,01 de la otra vía.
        via = TopoMagnet.appendPoint(via, 0.41f to 0.90f, listOf(otra), enabled = false)
        via = TopoMagnet.appendPoint(via, 0.41f to 0.70f, listOf(otra), enabled = false)
        val arranque = via
        // Ahora se enciende y se sigue.
        via = TopoMagnet.appendPoint(via, 0.40f to 0.50f, listOf(otra), enabled = true)

        assertEquals(arranque, via.take(arranque.size),
            "los puntos ya colocados no se tocan al encender el imán")
        assertTrue(via.last() in otra, "el punto nuevo sí debía engancharse: ${via.last()}")
    }

    @Test
    fun elPuntoNuevoTraeConsigoLosVerticesIntermedios() {
        // Del vértice de abajo al de arriba hay dos vértices por el medio: si no
        // se insertan, el tramo no queda compartido, solo se tocan las puntas.
        val via = TopoMagnet.appendPoint(
            listOf(0.40f to 0.90f), 0.40f to 0.30f, listOf(otra), enabled = true)
        assertTrue(via.size > 2, "esperaba los vértices intermedios de la otra vía: $via")
    }

    @Test
    fun sinOtrasViasNoHayNadaUnido() {
        assertTrue(TopoMagnet.joinedIndices(listOf(0.4f to 0.7f), emptyList()).isEmpty())
    }

    @Test
    fun elTramoDelMedioSeUneYLosExtremosNo() {
        // El flujo real: se dibuja el arranque con el imán apagado y el tramo
        // compartido con el imán encendido.
        val arranque = TopoMagnet.apply(
            listOf(0.41f to 0.95f, 0.41f to 0.90f), listOf(otra), enabled = false)
        val medio = TopoMagnet.apply(
            listOf(0.41f to 0.70f, 0.41f to 0.50f), listOf(otra), enabled = true)
        val via = arranque + medio

        val unidos = TopoMagnet.joinedIndices(via, listOf(otra))
        assertTrue(unidos.all { it >= arranque.size },
            "el arranque se dibujó con el imán apagado: no debería estar unido. Unidos: $unidos")
        assertTrue(unidos.isNotEmpty(), "el tramo del medio sí debía engancharse")
    }
}
