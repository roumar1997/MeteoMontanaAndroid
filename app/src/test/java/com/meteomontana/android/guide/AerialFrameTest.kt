package com.meteomontana.android.guide

import com.meteomontana.android.ui.screens.detail.guide.AerialFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AerialFrameTest {
    // Zarzalejo, más o menos.
    private val f = AerialFrame(40.545, 40.535, -4.170, -4.185, 1100, 800)

    @Test fun `the centre of the area lands in the centre of the image`() {
        val (x, y) = f.pixel(40.540, -4.1775)
        assertEquals(550f, x, 2f)
        assertEquals(400f, y, 2f)
    }

    @Test fun `north is up and east is right`() {
        val (xw, yn) = f.pixel(40.545, -4.185)
        val (xe, ys) = f.pixel(40.535, -4.170)
        assertTrue(xw < xe)
        assertTrue(yn < ys)
        assertTrue(xw >= 0f && xe <= 1100f && yn >= 0f && ys <= 800f)
    }

    @Test fun `tiles cover the whole image and zoom is capped`() {
        assertTrue(f.zoom in 1..19)
        val rects = f.tiles().map { (x, y) -> f.tileRect(x, y) }
        assertTrue(rects.minOf { it[0] } <= 0f && rects.minOf { it[1] } <= 0f)
        assertTrue(rects.maxOf { it[0] + it[2] } >= 1100f && rects.maxOf { it[1] + it[3] } >= 800f)
        assertEquals(19, AerialFrame(40.5401, 40.5400, -4.1701, -4.1700, 1100, 800).zoom)
    }
}
