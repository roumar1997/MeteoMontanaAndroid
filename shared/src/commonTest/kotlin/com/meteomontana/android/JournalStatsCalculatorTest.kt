package com.meteomontana.android

import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.usecase.journal.JournalStatsCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El cerebro de la pantalla ESTADÍSTICAS (C4), fijado con tests. */
class JournalStatsCalculatorTest {

    private fun entry(date: String, grade: String? = "6b", school: String = "Zarzalejo",
                      status: String = "DONE", lineId: String? = null) =
        JournalSession(
            id = date + (grade ?: "") + school + (lineId ?: ""), schoolId = school.lowercase(),
            schoolName = school, sector = null, blockName = "Piedra",
            grade = grade, notes = null, date = date, createdAt = date + "T10:00:00",
            discipline = "BOULDER", lineId = lineId, status = status
        )

    @Test
    fun losGradosSeOrdenanComoEnLaRealidad() {
        val c = JournalStatsCalculator
        assertTrue(c.gradeRank("7b+") > c.gradeRank("7b"))
        assertTrue(c.gradeRank("7a") > c.gradeRank("6c+"))
        assertTrue(c.gradeRank("6c") > c.gradeRank("6b+"))
        assertEquals(0, c.gradeRank("proyecto"))
    }

    @Test
    fun laPiramideVaDelMasDuroAlMasFacil() {
        val entries = listOf(
            entry("2026-07-01", "6a"), entry("2026-07-02", "6a"),
            entry("2026-07-03", "7a"), entry("2026-07-04", "6c")
        )
        val pyramid = JournalStatsCalculator.pyramid(entries)
        assertEquals(listOf("7a" to 1, "6c" to 1, "6a" to 2), pyramid)
    }

    @Test
    fun laRachaCuentaSemanasConsecutivasHaciaAtras() {
        // Hoy miércoles 2026-07-29; salidas esta semana, la pasada y hace 2 → racha 3.
        val days = listOf("2026-07-27", "2026-07-21", "2026-07-14")
        assertEquals(3, JournalStatsCalculator.currentStreakWeeks(days, "2026-07-29"))
        // Hueco hace 2 semanas → racha 2.
        val conHueco = listOf("2026-07-27", "2026-07-21", "2026-07-01")
        assertEquals(2, JournalStatsCalculator.currentStreakWeeks(conHueco, "2026-07-29"))
        // La semana en curso sin salida aún NO rompe la racha.
        val sinEstaSemana = listOf("2026-07-21", "2026-07-14")
        assertEquals(2, JournalStatsCalculator.currentStreakWeeks(sinEstaSemana, "2026-07-29"))
    }

    @Test
    fun losProyectosCaidosCasanPorLineIdYSeparanHomonimas() {
        val all = listOf(
            entry("2026-06-01", "7b", status = "PROJECT", lineId = "L1"),
            entry("2026-07-10", "7b", status = "DONE", lineId = "L1"),
            entry("2026-07-11", "6a", status = "DONE", lineId = "L2")
        )
        val done = JournalStatsCalculator.filter(all, "BOULDER", "2026")
        val s = JournalStatsCalculator.summary(done, all, "2026-07-29")
        assertEquals(1, s.projectsFallen)
        assertEquals(2, s.daysOut)
    }

    @Test
    fun elFiltroPorAnioYLosAniosDisponibles() {
        val entries = listOf(entry("2025-12-30"), entry("2026-01-02"), entry("2026-07-10"))
        assertEquals(listOf("2026", "2025"), JournalStatsCalculator.availableYears(entries))
        assertEquals(2, JournalStatsCalculator.filter(entries, null, "2026").size)
        assertEquals(3, JournalStatsCalculator.filter(entries, null, null).size)
    }

    @Test
    fun laProgresionDaLosUltimos12MesesYElMaximoPorTrimestre() {
        val entries = listOf(
            entry("2026-02-10", "6c"), entry("2026-05-01", "7a"), entry("2026-07-27", "7b+")
        )
        val p = JournalStatsCalculator.progression(entries, "2026-07-29")
        assertEquals(12, p.monthlyCounts.size)
        assertEquals("2026-07" to 1, p.monthlyCounts.last())
        assertEquals("2026-Q1" to "6c", p.maxGradePerQuarter.first())
        assertEquals("2026-Q3" to "7b+", p.maxGradePerQuarter.last())
        assertEquals(12, p.weeksOut.size)
        assertTrue(p.weeksOut.last())   // esta semana salió
        assertEquals("Zarzalejo", p.perSchool.first().first)
    }
}
