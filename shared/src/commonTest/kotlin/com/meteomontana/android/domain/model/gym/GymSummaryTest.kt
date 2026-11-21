package com.meteomontana.android.domain.model.gym

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GymSummaryTest {
    private val today = LocalDate(2026, 11, 20)

    private fun s(gym: Long, y: Int, m: Int, d: Int, vararg a: Pair<String, Int>) =
        GymSession(gym, LocalDate(y, m, d), ascents = a.map { GymAscent(it.first, it.second) })

    @Test fun totalsMaxGradeAndPerGym() {
        val sessions = listOf(
            s(1, 2026, 11, 18, "route:partner:sent:6b+/6c" to 2, "verde" to 3),
            s(1, 2026, 10, 2),
            s(2, 2026, 10, 3, "board:kilter:7a:40" to 1)
        )
        val sum = GymBreakdown.summarize(sessions, today) { if (it == 1L) "Chango" else "Sputnik" }
        assertEquals(3, sum.sessions)
        assertEquals(2, sum.gyms)
        assertEquals(6, sum.ascents)
        assertEquals("7a", sum.maxGrade)
        assertEquals(listOf("Chango", "Sputnik"), sum.byGym.map { it.name })
        assertEquals("6c", sum.byGym[0].maxGrade)
        assertEquals(12, sum.byMonth.size)
        assertEquals("2026-11", sum.byMonth.last().month)
        assertEquals(5, sum.byMonth.last().ascents)
        assertEquals(1, sum.byMonth[10].ascents)
        assertEquals("2025-12", sum.byMonth.first().month)
    }

    @Test fun filtersAndGradeBreakdown() {
        val items = listOf(
            GymAscent("route:solo:sent:6a", 2), GymAscent("route:solo:sent:6b+/6c", 1),
            GymAscent("board:kilter:6a:40", 4), GymAscent("verde", 3), GymAscent("7a", 5)
        )
        assertEquals(2, GymBreakdown.filter(items, AscentFilter.ROUTES).size)
        assertEquals(1, GymBreakdown.filter(items, AscentFilter.BOARDS).size)
        assertEquals(2, GymBreakdown.filter(items, AscentFilter.BLOCKS).size)
        // El grado doble cuenta por su grado alto (6c) y se ordena de facil a dificil.
        assertEquals(listOf("6a" to 6, "6c" to 1, "7a" to 5), GymBreakdown.byGrade(items))
        assertEquals(listOf("verde"), GymBreakdown.byColor(items).map { it.key })
    }

    @Test fun perGymBreakdownKeepsEachScaleApart() {
        val sessions = listOf(
            s(1, 2026, 11, 18, "verde" to 3, "azul" to 1),
            s(1, 2026, 11, 2, "verde" to 2),
            s(2, 2026, 11, 10, "6a" to 4, "route:solo:sent:6b" to 1, "board:system:6c" to 2, "board:kilter:6a:40" to 1)
        )
        val sum = GymBreakdown.summarize(sessions, today)
        val g1 = sum.byGym.first { it.gymId == 1L }
        assertEquals(mapOf("verde" to 5, "azul" to 1), g1.items.associate { it.key to it.count })
        assertEquals(listOf("2026-11-18", "2026-11-02"), g1.days.map { it.date })
        assertEquals(listOf(3 + 1, 2), g1.days.map { it.ascents })
        assertEquals(true, GymBreakdown.hasGymBreakdown(sum))
        // Bloques: el 1 solo con colores y el 2 solo con su grado; nunca se mezclan.
        val blocks = GymBreakdown.gymsFiltered(sum, AscentFilter.BLOCKS)
        assertEquals(mapOf(1L to 6, 2L to 4), blocks.associate { it.gymId to it.ascents })
        assertEquals(listOf("6a"), blocks.first { it.gymId == 2L }.items.map { it.key })
        assertEquals(listOf("route:solo:sent:6b"), GymBreakdown.gymsFiltered(sum, AscentFilter.ROUTES).single().items.map { it.key })
        assertEquals(2, GymBreakdown.gymsFiltered(sum, AscentFilter.BOARDS).single().items.size)
        // Boards por tablero: System (2) antes que Kilter (1).
        val byKind = GymBreakdown.boardsByKind(GymBreakdown.filter(sum.items, AscentFilter.BOARDS))
        assertEquals(listOf(BoardKind.SYSTEM, BoardKind.KILTER), byKind.map { it.first })
    }

    @Test fun oldServerWithoutBreakdown() {
        val sum = GymSummary(sessions = 1, gyms = 1, ascents = 3, byGym = listOf(GymSummaryGym(1, "X", 1, 3, null)))
        assertEquals(false, GymBreakdown.hasGymBreakdown(sum))
    }

    @Test fun emptyHasNoMaxGrade() {
        val sum = GymBreakdown.summarize(emptyList(), today)
        assertNull(sum.maxGrade)
        assertEquals(true, sum.isEmpty)
    }
}
