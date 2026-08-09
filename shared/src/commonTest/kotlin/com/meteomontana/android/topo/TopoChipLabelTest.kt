package com.meteomontana.android.topo

import com.meteomontana.android.domain.util.TopoChipLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El chip con el que eliges qué vía dibujar.
 *
 * Reportado por Rodrigo: con varias vías del mismo grado no hay forma de saber
 * cuál es cuál. El número tiene que seguir estando —es el que se pinta sobre la
 * roca— y la variante también, que es lo único que separa dos homónimas.
 */
class TopoChipLabelTest {

    @Test
    fun numeroNombreYGrado() {
        assertEquals("2 · La ola · 7a", TopoChipLabel.of(1, "La ola", grade = "7a"))
    }

    @Test
    fun laVarianteDistingueLasHomonimas() {
        val a = TopoChipLabel.of(0, "La ola", grade = "7a")
        val b = TopoChipLabel.of(1, "La ola", variant = "directa", grade = "7a")
        assertEquals("1 · La ola · 7a", a)
        assertEquals("2 · La ola (directa) · 7a", b)
    }

    @Test
    fun viaReciénCreadaSoloElNumero() {
        // Al crear una piedra las vías aún no tienen nombre: el chip no puede
        // quedar como " · " ni con separadores sueltos.
        assertEquals("3", TopoChipLabel.of(2, null))
        assertEquals("3", TopoChipLabel.of(2, "   ", variant = "  "))
    }

    @Test
    fun sinNombrePeroConGrado() {
        assertEquals("1 · 6b", TopoChipLabel.of(0, null, grade = "6b"))
    }

    @Test
    fun elNombreLargoSeRecortaPeroElNumeroYElGradoNo() {
        val etiqueta = TopoChipLabel.of(
            0, "Travesía de los mil pasos hacia el amanecer", grade = "8a")
        assertTrue(etiqueta.startsWith("1 · "), etiqueta)
        assertTrue(etiqueta.endsWith(" · 8a"), "el grado no se puede perder: $etiqueta")
        assertTrue(etiqueta.contains("…"), "esperaba el recorte: $etiqueta")
    }

    @Test
    fun elNumeroEsElQueSeVeSobreLaRoca() {
        // Base 0 dentro, base 1 fuera: si esto se descuadra, el chip y el badge
        // dibujado en la foto dejan de coincidir.
        assertTrue(TopoChipLabel.of(0, "X").startsWith("1"))
        assertTrue(TopoChipLabel.of(9, "X").startsWith("10"))
    }
}
