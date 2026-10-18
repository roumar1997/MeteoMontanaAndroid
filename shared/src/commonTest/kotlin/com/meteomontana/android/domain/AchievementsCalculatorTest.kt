package com.meteomontana.android.domain

import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.usecase.journal.AchievementsCalculator
import com.meteomontana.android.domain.usecase.journal.GoalCalculator
import com.meteomontana.android.domain.usecase.journal.SchoolPlace
import com.meteomontana.android.domain.usecase.journal.YearGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementsCalculatorTest {

    private var n = 0
    private fun e(date: String, grade: String = "6a", disc: String = "BOULDER", school: String = "s1",
                  status: String = "DONE", flash: Boolean = false, onsight: Boolean = false, block: String = "b${n++}") =
        JournalSession("id${n++}", school, school, null, block, grade, null, date, date, disc, null, status, onsight, flash)

    private fun unlocked(list: List<com.meteomontana.android.domain.usecase.journal.Achievement>, id: String) =
        list.first { it.id == id }

    @Test
    fun primerGradoPorModalidadConSuFecha() {
        val all = listOf(e("2026-01-10", "6b"), e("2026-02-01", "7a+"), e("2026-03-01", "7a", disc = "ROUTE"))
        val a = AchievementsCalculator.compute(all, emptyMap(), "2026-10-18")
        assertTrue(unlocked(a, "grade_boulder_6a").unlocked)
        assertEquals("2026-02-01", unlocked(a, "grade_boulder_7a").unlockedOn)
        assertFalse(unlocked(a, "grade_boulder_7b").unlocked)
        assertTrue(unlocked(a, "grade_route_7a").unlocked)
        assertFalse(unlocked(a, "grade_route_7b").unlocked)
    }

    @Test
    fun losProyectosNoCuentanComoAscension() {
        val all = (1..9).map { e("2026-01-${10 + it}") } + e("2026-02-01", status = "PROJECT")
        assertFalse(unlocked(AchievementsCalculator.compute(all, emptyMap(), "2026-10-18"), "volume_10").unlocked)
    }

    @Test
    fun proyectoCaidoYFueraDeEspana() {
        val all = listOf(e("2026-01-01", status = "PROJECT", block = "La ola", school = "s1"),
            e("2026-02-01", block = "La ola", school = "s1"), e("2026-03-01", school = "fr1"))
        val a = AchievementsCalculator.compute(all, mapOf("fr1" to SchoolPlace("Occitanie", "FR")), "2026-10-18")
        assertEquals("2026-02-01", unlocked(a, "projects_1").unlockedOn)
        assertTrue(unlocked(a, "abroad_1").unlocked)
    }

    @Test
    fun cuatroFinesDeSemanaSeguidos() {
        // Sábados 3, 10, 17 y 24 de octubre de 2026.
        val all = listOf("2026-10-03", "2026-10-10", "2026-10-17", "2026-10-24").map { e(it) }
        assertEquals("2026-10-24", unlocked(AchievementsCalculator.compute(all, emptyMap(), "2026-10-25"), "weekends_4").unlockedOn)
        val gap = listOf("2026-10-03", "2026-10-10", "2026-10-24", "2026-10-31").map { e(it) }
        assertFalse(unlocked(AchievementsCalculator.compute(gap, emptyMap(), "2026-11-01"), "weekends_4").unlocked)
    }

    @Test
    fun tresDelMismoGradoEnUnDiaYDiezEnTotal() {
        val twoToday = listOf(e("2026-05-01", "7a"), e("2026-05-01", "7a+"), e("2026-05-02", "7a"))
        val a = AchievementsCalculator.compute(twoToday, emptyMap(), "2026-10-18")
        assertFalse(unlocked(a, "day_boulder_7a").unlocked)

        val three = twoToday + e("2026-05-01", "7b")
        val b = AchievementsCalculator.compute(three, emptyMap(), "2026-10-18")
        assertEquals("2026-05-01", unlocked(b, "day_boulder_7a").unlockedOn)
        assertFalse(unlocked(b, "day_boulder_7b").unlocked)      // solo hay una de 7b
        assertTrue(unlocked(b, "day_boulder_6a").unlocked)       // las de 7a también cuentan para 6a

        val ten = (1..10).map { e("2026-06-" + it.toString().padStart(2, '0'), "7a") }
        val c = AchievementsCalculator.compute(ten, emptyMap(), "2026-10-18")
        assertTrue(unlocked(c, "total_boulder_7a").unlocked)
        assertFalse(unlocked(c, "total_boulder_7b").unlocked)
        assertFalse(unlocked(c, "total_route_7a").unlocked)      // son bloques, no vías
    }

    @Test
    fun lasRepeticionesDeLaMismaViaNoCuentanParaElTotal() {
        val same = (1..12).map { e("2026-06-" + it.toString().padStart(2, '0'), "7a", block = "La ola") }
        assertFalse(unlocked(AchievementsCalculator.compute(same, emptyMap(), "2026-10-18"), "total_boulder_7a").unlocked)
    }

    @Test
    fun cadaLogroTieneDificultadYGrupoYLosDeGradoMasDuroSonMasRaros() {
        val a = AchievementsCalculator.compute(emptyList(), emptyMap(), "2026-10-18")
        assertTrue(a.size > 60)
        assertTrue(a.map { it.id }.toSet().size == a.size)       // ids únicos (se mandan al servidor)
        assertTrue(unlocked(a, "grade_boulder_8a").tier.ordinal > unlocked(a, "grade_boulder_6a").tier.ordinal)
        assertTrue(unlocked(a, "day_route_7c").tier.ordinal >= unlocked(a, "total_route_7c").tier.ordinal)
    }

    @Test
    fun progresoDelObjetivoDelAno() {
        val all = listOf(e("2025-12-30", "7b"), e("2026-01-05", "7a"), e("2026-01-05", "6c"), e("2026-02-01", "7a+"))
        val p = GoalCalculator.progress(YearGoal(2026, "BOULDER", "7a+", 100, 20), all)
        assertEquals(3, p.ascentsDone)
        assertEquals(2, p.daysDone)
        assertEquals("7a+", p.maxGradeThisYear)
        assertTrue(p.gradeReached)
    }
}
