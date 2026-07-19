package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.DrawOp
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.renderTopo
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los badges (número de vía, etiqueta de inicio) se pintan SIEMPRE encima de
 * TODAS las líneas (feature 8ª tanda 2.19.0): renderTopo acumula los badges y
 * los emite AL FINAL, para que el pie de una vía no quede tapado por el trazo
 * de otra que pase por encima (el caso real: el PIE verde bajo la travesía
 * morada). iOS replica esto con dos pasadas (strokesOnly/badgesOnly) — si este
 * orden cambia aquí, revisar también TopoPhotoView.swift.
 */
class BadgesOnTopTest {

    private val w = 400f
    private val h = 300f

    /** Tres vías que se cruzan, con inicio etiquetado (badges de nº + inicio). */
    private val lines = listOf(
        TopoLineData("Una", "6a", "SIT", listOf(0.1f to 0.9f, 0.3f to 0.3f)),
        TopoLineData("Dos", "7a", "STAND", listOf(0.2f to 0.9f, 0.2f to 0.2f)),
        TopoLineData("Tres", "6c", "TRAV", listOf(0.05f to 0.5f, 0.5f to 0.5f))
    )

    @Test
    fun `todas las lineas se emiten ANTES que cualquier badge`() {
        val ops = renderTopo(lines, w, h)

        val lastStroke = ops.indexOfLast { it is DrawOp.LinePath }
        val firstBadge = ops.indexOfFirst {
            it is DrawOp.FilledCircle || it is DrawOp.TextLabel || it is DrawOp.CircleStroke
        }

        assertTrue("debe haber trazos", lastStroke >= 0)
        assertTrue("debe haber badges", firstBadge >= 0)
        assertTrue(
            "TODOS los trazos (último en $lastStroke) deben ir antes que el " +
                    "primer badge (en $firstBadge) — si no, una línea tapa los badges",
            lastStroke < firstBadge)
    }

    @Test
    fun `cada via aporta su numero como TextLabel`() {
        val ops = renderTopo(lines, w, h)
        val labels = ops.filterIsInstance<DrawOp.TextLabel>().map { it.text }
        assertTrue("falta el badge 1", labels.contains("1"))
        assertTrue("falta el badge 2", labels.contains("2"))
        assertTrue("falta el badge 3", labels.contains("3"))
    }
}
