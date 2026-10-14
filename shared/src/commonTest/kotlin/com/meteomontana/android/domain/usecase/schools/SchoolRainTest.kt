package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.RangeScore
import com.meteomontana.android.domain.model.SchoolScore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchoolRainTest {

    private fun today(mm: Double) = SchoolScore("s", 70, emptyList(), dryRock = true, rainMm = mm)
    private fun range(rainDays: Int) = RangeScore("s", 60, 62, emptyList(), rainDays, 0.0)

    @Test fun `hoy - llueve si la prevision trae milimetros`() {
        assertTrue(SchoolRain.isRainy(today(0.4), null))
        assertFalse(SchoolRain.isRainy(today(0.0), null))
    }

    @Test fun `tramo - llueve si llueve algun dia aunque hoy sea seco`() {
        assertTrue(SchoolRain.isRainy(today(0.0), range(1)))
        assertFalse(SchoolRain.isRainy(today(5.0), range(0)))   // manda el tramo, no hoy
    }

    @Test fun `sin datos aun no cuenta como lluvia`() {
        assertFalse(SchoolRain.isRainy(null, null))
    }
}
