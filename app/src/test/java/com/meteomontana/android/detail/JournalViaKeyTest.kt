package com.meteomontana.android.detail

import com.meteomontana.android.ui.screens.detail.journalViaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Clave del diario "escuela|#lineId" (con "escuela|nombre" solo como legado).
 *
 * Es el fix de raíz del bug de "La ola" (2026-07-18): dos vías HOMÓNIMAS de la
 * misma escuela compartían clave por nombre → marcar una encendía el ✓ de la
 * otra, y desmarcar podía BORRAR la entrada de la homónima en silencio.
 * journalViaKey es la ÚNICA fuente del formato (VM + SchoolMap): si alguien
 * cambia el formato en un sitio y no en otro, estos tests lo cazan.
 */
class JournalViaKeyTest {

    @Test
    fun `dos homonimas con lineId distinto tienen claves DISTINTAS`() {
        // El bug original: "La ola" ×2 en Zarzalejo.
        val a = journalViaKey("zarzalejo", "line-1", "La ola")
        val b = journalViaKey("zarzalejo", "line-2", "La ola")
        assertNotEquals("homónimas deben ser independientes", a, b)
    }

    @Test
    fun `con lineId la clave NO depende del nombre`() {
        // Renombrar la vía (corrección admin) no debe desenganchar el diario.
        val antes = journalViaKey("esc", "line-7", "La ola")
        val despues = journalViaKey("esc", "line-7", "La ola (directa)")
        assertEquals(antes, despues)
    }

    @Test
    fun `sin lineId cae al formato legado por nombre normalizado`() {
        // Entradas antiguas del diario (previas al fix) no tienen lineId: deben
        // seguir casando por nombre, insensible a mayúsculas y espacios.
        val a = journalViaKey("esc", null, "  La Ola ")
        val b = journalViaKey("esc", "", "la ola")
        assertEquals(a, b)
        assertEquals("esc|la ola", a)
    }

    @Test
    fun `formato exacto con lineId`() {
        // El VM parsea este formato al desmarcar (localiza POR lineId); si
        // cambia, el desmarcado offline dejaría de encontrar la fila.
        assertEquals("esc|#line-9", journalViaKey("esc", "line-9", "cualquiera"))
    }

    @Test
    fun `misma via en escuelas distintas no colisiona`() {
        assertNotEquals(
            journalViaKey("esc-a", null, "travesía"),
            journalViaKey("esc-b", null, "travesía"))
    }

    @Test
    fun `schoolId null no revienta y produce clave estable`() {
        assertEquals("|#l1", journalViaKey(null, "l1", "x"))
        assertEquals("|x", journalViaKey(null, null, "X"))
    }
}
