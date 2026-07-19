package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.DrawOp
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.fanOffsets
import com.meteomontana.android.domain.util.magnetizeStroke
import com.meteomontana.android.domain.util.renderTopo
import com.meteomontana.android.domain.util.sharedSegmentKeys
import com.meteomontana.android.domain.util.simplifyStroke
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

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

    @Test fun `renderTopo pinta el tramo compartido a FRANJAS con fase distinta`() {
        val a = TopoLineData("A", "6a", null, existing)
        val b = TopoLineData("B", "7a", null,
            listOf(0.05f to 0.95f, existing[1], existing[2], 0.60f to 0.20f))
        val ops = renderTopo(listOf(a, b), w, h, stripePx = 22f)
        // Franja = guion cuyo hueco es múltiplo del largo (22 → hueco 22*(n-1)).
        val stripeRuns = ops.filterIsInstance<DrawOp.LinePath>()
            .filter { it.dashPattern?.first == 22f }
        // Las DOS vías pintan su franja del tramo (cada una con SU color de
        // grado y fase distinta → se intercalan).
        assertEquals(2, stripeRuns.size)
        assertTrue(stripeRuns[0].argb != stripeRuns[1].argb)          // color propio
        assertTrue(stripeRuns[0].dashPhase != stripeRuns[1].dashPhase) // fases alternas
        stripeRuns.forEach { assertEquals(2, it.pts.size) }            // 2 vértices
    }

    @Test fun `todas las lineas van discontinuas estilo guia`() {
        val a = TopoLineData("A", "6a", null, existing)
        val ops = renderTopo(listOf(a), w, h, dashPx = 12f to 9f)
        val runs = ops.filterIsInstance<DrawOp.LinePath>()
        assertTrue(runs.isNotEmpty())
        runs.forEach {
            assertTrue(it.dashed, "Ninguna línea debe ser maciza")
            assertEquals(12f to 9f, it.dashPattern)
        }
    }

    @Test fun `magnetize v2 pega tocando el TRAMO, no solo el vertice`() {
        // Toque en mitad de un tramo largo (lejos de ambos vértices) → debe
        // pegarse al vértice más cercano de ese tramo.
        val longLine = listOf(0.10f to 0.50f, 0.90f to 0.50f)
        val drawn = listOf(0.35f to 0.51f)   // a 0.01 del tramo, lejos de vértices
        val out = magnetizeStroke(drawn, listOf(longLine))
        assertEquals(listOf(0.10f to 0.50f), out)
    }

    @Test fun `simplify quita el temblor pero respeta las esquinas`() {
        // Trazo con ruido a lo largo de una recta + una esquina real.
        val noisy = listOf(
            0.10f to 0.10f, 0.15f to 0.101f, 0.20f to 0.099f, 0.25f to 0.102f,
            0.30f to 0.10f,                       // recta con temblor
            0.30f to 0.30f                        // esquina real (giro brusco)
        )
        val out = simplifyStroke(noisy)
        assertTrue(out.size < noisy.size, "Debe quitar los puntos de temblor")
        assertEquals(noisy.first(), out.first())
        assertEquals(noisy.last(), out.last())
        assertTrue(out.contains(0.30f to 0.10f), "Debe conservar la esquina")
    }

    @Test fun `fanOffsets separa badges que coinciden y no toca el resto`() {
        val p = 0.5f to 0.5f
        val offsets = fanOffsets(listOf(p, p, 0.9f to 0.9f, p), 30f)
        // Los tres coincidentes se reparten alrededor del punto; el suelto queda a 0.
        assertEquals(0f, offsets[2])
        assertEquals(3, listOf(offsets[0], offsets[1], offsets[3]).distinct().size)
        assertEquals(0f, offsets[0] + offsets[1] + offsets[3], 0.001f) // centrados
    }

    @Test fun `sin tramos compartidos renderTopo pinta como siempre`() {
        val a = TopoLineData("A", "6a", null, existing)
        val ops = renderTopo(listOf(a), w, h)
        val runs = ops.filterIsInstance<DrawOp.LinePath>()
        assertEquals(1, runs.size)
        assertEquals(existing.size, runs[0].pts.size)
    }
}
