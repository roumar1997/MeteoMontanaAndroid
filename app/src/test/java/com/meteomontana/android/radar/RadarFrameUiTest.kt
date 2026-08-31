package com.meteomontana.android.radar

import com.meteomontana.android.ui.screens.radar.RadarFrameUi
import com.meteomontana.android.ui.screens.radar.radarKeepsFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RadarFrameUi.timeLabel: extrae "HH:MM" del capturedAt ISO para la timeline
 * del radar. (El resto del RadarViewModel toca Bitmap/File de Android → se
 * cubre en pruebas de instrumentación, no aquí.)
 */
class RadarFrameUiTest {

    @Test fun `timeLabel extrae la hora y minuto del capturedAt`() {
        assertEquals("18:40", RadarFrameUi("ts", "2026-07-03T18:40").timeLabel)
    }

    @Test fun `timeLabel con segundos igual toma solo HH_MM`() {
        assertEquals("09:05", RadarFrameUi("ts", "2026-07-03T09:05:12").timeLabel)
    }

    // ── Thinning de la película (radarKeepsFrame) ──────────────────────────

    @Test fun `la ultima hora (7 frames) se conserva completa`() {
        // size=10, keepLast=7 → cut=3: los índices 3..9 SIEMPRE se conservan.
        for (i in 3..9) assertTrue("frame $i debe conservarse", radarKeepsFrame(i, 10))
    }

    @Test fun `antes de la ultima hora se conserva 1 de cada 3`() {
        // size=10, cut=3: de 0,1,2 solo el 0 (i%3==0).
        assertTrue(radarKeepsFrame(0, 10))
        assertFalse(radarKeepsFrame(1, 10))
        assertFalse(radarKeepsFrame(2, 10))
    }

    @Test fun `pelicula corta (menos que keepLast) se conserva entera`() {
        // size=5 < 7 → cut=0 → todos los índices i>=0 se conservan.
        for (i in 0..4) assertTrue(radarKeepsFrame(i, 5))
    }

    @Test fun `el conteo de frames adelgazados es el esperado`() {
        // size=10 → conservados: 0,3,4,5,6,7,8,9 = 8.
        val kept = (0 until 10).count { radarKeepsFrame(it, 10) }
        assertEquals(8, kept)
    }
}
