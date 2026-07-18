package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.DrawOp
import com.meteomontana.android.domain.util.SHARED_SEGMENT_ARGB
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.magnetizeStroke
import com.meteomontana.android.domain.util.renderTopo
import com.meteomontana.android.domain.util.sharedSegmentKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tramos compartidos entre vías: imán del editor + detección + pintado. */
class SharedSegmentsTest {

    private val w = 400f
    private val h = 300f

    // Vía existente: diagonal con 4 vértices.
    private val existing = listOf(0.10f to 0.90f, 0.20f to 0.70f, 0.30f to 0.50f, 0.40f to 0.30f)

    @Test fun `magnetize sustituye puntos cercanos por los vertices exactos`() {
        // Trazo que pasa MUY cerca de los dos primeros vértices y luego se aleja.
        val drawn = listOf(0.101f to 0.899f, 0.201f to 0.701f, 0.80f to 0.20f)
        val out = magnetizeStroke(drawn, listOf(existing))
        assertEquals(existing[0], out[0])
        assertEquals(existing[1], out[1])
        assertEquals(0.80f to 0.20f, out[2])
    }

    @Test fun `magnetize inserta vertices intermedios si el dedo va rapido`() {
        // El dedo solo toca el vértice 0 y el 3 → deben insertarse el 1 y el 2.
        val drawn = listOf(0.101f to 0.899f, 0.401f to 0.301f, 0.80f to 0.20f)
        val out = magnetizeStroke(drawn, listOf(existing))
        assertEquals(listOf(existing[0], existing[1], existing[2], existing[3], 0.80f to 0.20f), out)
    }

    @Test fun `magnetize respeta el sentido inverso del trazo`() {
        val drawn = listOf(0.401f to 0.301f, 0.101f to 0.899f)
        val out = magnetizeStroke(drawn, listOf(existing))
        assertEquals(listOf(existing[3], existing[2], existing[1], existing[0]), out)
    }

    @Test fun `magnetize no toca trazos lejos de cualquier via`() {
        val drawn = listOf(0.80f to 0.80f, 0.90f to 0.60f)
        assertEquals(drawn, magnetizeStroke(drawn, listOf(existing)))
    }

    @Test fun `segmentos identicos en dos vias se detectan como compartidos`() {
        val a = TopoLineData("A", "6a", null, existing)
        // B comparte el tramo central (vértices 1-2) y luego se separa.
        val b = TopoLineData("B", "7a", null,
            listOf(0.05f to 0.95f, existing[1], existing[2], 0.60f to 0.20f))
        val keys = sharedSegmentKeys(listOf(a, b))
        assertEquals(1, keys.size)
    }

    @Test fun `una sola via no comparte nada consigo misma`() {
        assertTrue(sharedSegmentKeys(listOf(TopoLineData("A", "6a", null, existing))).isEmpty())
    }

    @Test fun `renderTopo pinta el tramo compartido del color especial`() {
        val a = TopoLineData("A", "6a", null, existing)
        val b = TopoLineData("B", "7a", null,
            listOf(0.05f to 0.95f, existing[1], existing[2], 0.60f to 0.20f))
        val ops = renderTopo(listOf(a, b), w, h)
        val sharedRuns = ops.filterIsInstance<DrawOp.LinePath>()
            .filter { it.argb == SHARED_SEGMENT_ARGB }
        // Las DOS vías pintan su racha compartida (se superponen, mismo color).
        assertEquals(2, sharedRuns.size)
        sharedRuns.forEach { run ->
            assertEquals(2, run.pts.size)   // el tramo común son 2 vértices
        }
    }

    @Test fun `sin tramos compartidos renderTopo pinta como siempre`() {
        val a = TopoLineData("A", "6a", null, existing)
        val ops = renderTopo(listOf(a), w, h)
        val runs = ops.filterIsInstance<DrawOp.LinePath>()
        assertEquals(1, runs.size)
        assertEquals(existing.size, runs[0].pts.size)
    }
}
