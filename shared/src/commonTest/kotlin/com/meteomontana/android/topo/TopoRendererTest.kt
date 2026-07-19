package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.DrawOp
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.gradeArgb
import com.meteomontana.android.domain.util.renderTopo
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class TopoRendererTest {

    private val w = 400f
    private val h = 300f

    @Test fun `linea vacia no genera ops`() {
        val result = renderTopo(listOf(TopoLineData(null, null, null, emptyList())), w, h)
        assertTrue(result.isEmpty())
    }

    @Test fun `linea de dos puntos genera LinePath con coords absolutas`() {
        val line = TopoLineData(null, "7a", null, listOf(0f to 0f, 1f to 1f))
        val ops = renderTopo(listOf(line), w, h)

        val linePaths = ops.filterIsInstance<DrawOp.LinePath>()
        assertTrue(linePaths.isNotEmpty(), "Debe haber al menos un LinePath")

        val main = linePaths.last()
        assertEquals(2, main.pts.size)
        assertEquals(0f, main.pts[0].first, 0.001f)
        assertEquals(0f, main.pts[0].second, 0.001f)
        assertEquals(w, main.pts[1].first, 0.001f)
        assertEquals(h, main.pts[1].second, 0.001f)
    }

    @Test fun `grado blanco genera outline oscuro previo`() {
        val line = TopoLineData(null, "5b", null, listOf(0f to 0f, 0.5f to 0.5f))
        val ops = renderTopo(listOf(line), w, h)

        val paths = ops.filterIsInstance<DrawOp.LinePath>()
        assertEquals(2, paths.size, "Grado blanco debe generar outline + línea = 2 LinePath")
        assertEquals(0xCC000000L, paths[0].argb)  // outline oscuro primero
    }

    @Test fun `grado coloreado no genera outline`() {
        val line = TopoLineData(null, "7a", null, listOf(0f to 0f, 0.5f to 0.5f))
        val ops = renderTopo(listOf(line), w, h)

        val paths = ops.filterIsInstance<DrawOp.LinePath>()
        assertEquals(1, paths.size, "Grado coloreado solo genera 1 LinePath")
    }

    @Test fun `badge numerico siempre presente`() {
        val line = TopoLineData(null, "6b", null, listOf(0f to 0f, 1f to 1f))
        val ops = renderTopo(listOf(line), w, h)

        val labels = ops.filterIsInstance<DrawOp.TextLabel>()
        assertTrue(labels.any { it.text == "1" }, "Debe haber al menos un TextLabel (badge numérico)")
    }

    @Test fun `badge de inicio se genera solo si startType es valido`() {
        val conTipo = TopoLineData(null, "6b", "PIE", listOf(0f to 0f, 1f to 1f))
        val sinTipo = TopoLineData(null, "6b", null, listOf(0f to 0f, 1f to 1f))

        val opsConTipo = renderTopo(listOf(conTipo), w, h)
        val opsSinTipo = renderTopo(listOf(sinTipo), w, h)

        val labelsConTipo = opsConTipo.filterIsInstance<DrawOp.TextLabel>()
        val labelsSinTipo = opsSinTipo.filterIsInstance<DrawOp.TextLabel>()

        assertTrue(labelsConTipo.any { it.text in listOf("PIE", "SIT", "LAN", "TRV") }, "Con startType debe haber badge PIE/SIT/LAN/TRV")
        assertTrue(labelsSinTipo.none { it.text in listOf("PIE", "SIT", "LAN", "TRV") }, "Sin startType no debe haber badge de inicio")
    }

    @Test fun `gradeArgb coincide con palette de la PWA`() {
        assertEquals(0xFFFFFFFFL, gradeArgb("5b").first)   // blanco ≤5c+
        assertEquals(0xFF1FA84EL, gradeArgb("6a").first)   // verde
        assertEquals(0xFF1D6DD6L, gradeArgb("6c").first)   // azul
        assertEquals(0xFF8E3FBFL, gradeArgb("7a").first)   // morado
        assertEquals(0xFFD62828L, gradeArgb("7b").first)   // rojo
        assertEquals(0xFF111111L, gradeArgb("8a").first)   // negro ≥8a
        assertEquals(0xFFFF4FA3L, gradeArgb(null).first)   // proyecto/sin grado
        assertTrue(gradeArgb("5b").third, "≤5c+ es dark")
    }

    @Test fun `dos lineas producen badges independientes`() {
        val lines = listOf(
            TopoLineData(null, "6a", "PIE", listOf(0f to 0f, 0.5f to 0.5f)),
            TopoLineData(null, "7a", "SIT", listOf(0.1f to 0.1f, 0.9f to 0.9f))
        )
        val ops = renderTopo(lines, w, h)

        val labels = ops.filterIsInstance<DrawOp.TextLabel>()
        assertTrue(labels.any { it.text == "1" }, "Badge 1 presente")
        assertTrue(labels.any { it.text == "2" }, "Badge 2 presente")
        assertTrue(labels.any { it.text == "PIE" }, "Badge PIE presente")
        assertTrue(labels.any { it.text == "SIT" }, "Badge SIT presente")
    }
}
