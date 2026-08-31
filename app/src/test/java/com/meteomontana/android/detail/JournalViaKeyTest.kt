package com.meteomontana.android.detail

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.journal.journalViaKey
import com.meteomontana.android.ui.screens.detail.matchedLineIds
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

    // ── matchedLineIds: la traducción diario → ✓ de la ficha de piedra ──

    private fun line(id: String, name: String) = BlockLine(
        id = id, name = name, grade = "6a", startType = null,
        linePath = null, sortOrder = 0)

    private fun block(vararg lines: BlockLine) = Block(
        id = "b1", schoolId = "esc", type = "BLOCK", name = "Piedra", lat = 0.0, lon = 0.0,
        photoPath = null, description = null, createdByUid = "", createdAt = "",
        lines = lines.toList())

    @Test
    fun `matchedLineIds casa por lineId y separa homonimas`() {
        // Dos "La ola" (el caso real): solo la marcada por id enciende su ✓.
        val b = block(line("l1", "La ola"), line("l2", "La ola"))
        val done = matchedLineIds(b, setOf("esc|#l2"))
        assertEquals(setOf("l2"), done)
    }

    @Test
    fun `matchedLineIds casa por nombre LEGADO cuando la clave es antigua`() {
        val b = block(line("l1", "Travesía"))
        assertEquals(setOf("l1"), matchedLineIds(b, setOf("esc|travesía")))
    }

    @Test
    fun `matchedLineIds con via sin nombre usa el fallback Via N`() {
        val b = block(line("l1", ""))
        // La clave por id manda aunque el nombre esté vacío.
        assertEquals(setOf("l1"), matchedLineIds(b, setOf("esc|#l1")))
        // Y el legado por nombre usa "vía 1".
        assertEquals(setOf("l1"), matchedLineIds(b, setOf("esc|vía 1")))
    }

    @Test
    fun `matchedLineIds sin coincidencias devuelve vacio`() {
        val b = block(line("l1", "La ola"))
        assertEquals(emptySet<String>(), matchedLineIds(b, setOf("otra|#l1", "esc|#l9")))
    }
}
