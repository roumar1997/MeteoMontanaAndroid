package com.meteomontana.android.radar

import com.meteomontana.android.ui.screens.radar.RadarFrameUi
import org.junit.Assert.assertEquals
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
}
