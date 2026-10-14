package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.RangeDayScore
import com.meteomontana.android.domain.model.RangeScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DayRainSummaryTest {

    private fun school(id: String, vararg days: Triple<String, Double, Boolean>) = RangeScore(
        id = id, combinedScore = 60, avgScore = 60,
        days = days.map { (d, mm, rainy) -> RangeDayScore(d, 60, mm, if (rainy) 80 else 10, rainy) },
        rainDays = days.count { it.third }, maxRainMm = days.maxOfOrNull { it.second } ?: 0.0
    )

    private val ranges = listOf(
        school("a", Triple("2026-10-16", 0.0, false), Triple("2026-10-17", 6.0, true)),
        school("b", Triple("2026-10-16", 0.2, false), Triple("2026-10-17", 4.0, true)),
        school("c", Triple("2026-10-16", 3.0, true), Triple("2026-10-17", 10.0, true))
    )

    @Test fun `mediana de milimetros y escuelas con lluvia por dia`() {
        val (viernes, sabado) = DayRainSummary.summarize(ranges, listOf("2026-10-16", "2026-10-17"))
        assertEquals(0.2, viernes.medianMm)
        assertEquals(1, viernes.rainySchools)
        assertEquals(6.0, sabado.medianMm)
        assertEquals(3, sabado.rainySchools)
    }

    @Test fun `un dia llueve si llueve en la mitad o mas de las escuelas`() {
        val (viernes, sabado) = DayRainSummary.summarize(ranges, listOf("2026-10-16", "2026-10-17"))
        assertFalse(viernes.rainy)     // 1 de 3
        assertTrue(sabado.rainy)       // 3 de 3
        assertTrue(DayRain("d", 1.0, 2, 4).rainy)    // justo la mitad
    }

    @Test fun `un dia sin datos no llueve y no se inventa nada`() {
        val d = DayRainSummary.summarize(ranges, listOf("2026-10-30")).single()
        assertEquals(0, d.schools)
        assertFalse(d.rainy)
    }

    @Test fun `mediana con numero par de escuelas es la media de las dos centrales`() {
        val r = listOf(school("a", Triple("d", 2.0, true)), school("b", Triple("d", 4.0, true)))
        assertEquals(3.0, DayRainSummary.summarize(r, listOf("d")).single().medianMm)
    }
}
