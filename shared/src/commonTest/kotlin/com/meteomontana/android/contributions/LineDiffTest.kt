package com.meteomontana.android.contributions

import com.meteomontana.android.domain.usecase.contributions.LineField
import com.meteomontana.android.domain.usecase.contributions.LineFields
import com.meteomontana.android.domain.usecase.contributions.computeLineDiff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Red de seguridad del diff de admin al "corregir vía". Cubre el bug real que
 * motivó la feature (faltaba la VARIANTE) y el contrato "solo campos que cambian".
 */
class LineDiffTest {

    @Test
    fun `via nueva se marca isNew sin campos`() {
        val d = computeLineDiff(orig = null, new = LineFields(name = "La ola", grade = "7a"),
            drawingChanged = true)
        assertTrue(d.isNew)
        assertTrue(d.changes.isEmpty())
        assertTrue(d.hasAnyChange)
        assertEquals("La ola", d.displayName)
    }

    @Test
    fun `sin cambios reales no reporta nada`() {
        val f = LineFields(name = "La ola", grade = "7a", variant = "directa", startType = "SIT")
        val d = computeLineDiff(orig = f, new = f, drawingChanged = false)
        assertFalse(d.isNew)
        assertFalse(d.hasAnyChange)
        assertTrue(d.changes.isEmpty())
    }

    @Test
    fun `solo el grado cambia`() {
        val d = computeLineDiff(
            orig = LineFields(name = "La ola", grade = "7a"),
            new = LineFields(name = "La ola", grade = "7a+"),
            drawingChanged = false)
        assertEquals(1, d.changes.size)
        assertEquals(LineField.GRADE, d.changes[0].field)
        assertEquals("7a", d.changes[0].old)
        assertEquals("7a+", d.changes[0].new)
    }

    @Test
    fun `la VARIANTE entra en el diff (bug historico)`() {
        val d = computeLineDiff(
            orig = LineFields(name = "La ola"),
            new = LineFields(name = "La ola", variant = "directa"),
            drawingChanged = false)
        assertTrue(d.changes.any { it.field == LineField.VARIANT && it.old == null && it.new == "directa" })
    }

    @Test
    fun `blanco y null se tratan igual - no es un cambio`() {
        val d = computeLineDiff(
            orig = LineFields(name = "La ola", description = ""),
            new = LineFields(name = "La ola", description = null),
            drawingChanged = false)
        assertFalse(d.hasAnyChange)
    }

    @Test
    fun `trazado redibujado cuenta como cambio aunque el texto sea igual`() {
        val f = LineFields(name = "La ola", grade = "7a")
        val d = computeLineDiff(orig = f, new = f, drawingChanged = true)
        assertTrue(d.hasAnyChange)
        assertTrue(d.drawingChanged)
        assertTrue(d.changes.isEmpty())
    }

    @Test
    fun `varios campos a la vez`() {
        val d = computeLineDiff(
            orig = LineFields(name = "La ola", grade = "7a", startType = "SIT"),
            new = LineFields(name = "La ola directa", grade = "7a+", startType = "SEMI"),
            drawingChanged = false)
        val fields = d.changes.map { it.field }.toSet()
        assertEquals(setOf(LineField.NAME, LineField.GRADE, LineField.START_TYPE), fields)
    }
}
