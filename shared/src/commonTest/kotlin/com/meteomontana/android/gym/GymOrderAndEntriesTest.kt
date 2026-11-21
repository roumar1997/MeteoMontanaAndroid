package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.AscentEntries
import com.meteomontana.android.domain.model.gym.AscentFilter
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymOrder
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.domain.model.gym.GymSummaryDay
import com.meteomontana.android.domain.model.gym.GymSummaryGym
import kotlin.test.Test
import kotlin.test.assertEquals

class GymOrderAndEntriesTest {
    private fun gym(id: Long, ascents: Int = 1, items: List<GymAscent> = emptyList(), days: List<GymSummaryDay> = emptyList()) =
        GymSummaryGym(id, "G$id", 1, ascents, null, items, days)

    @Test fun parseTolerantAndRoundTrip() {
        assertEquals(listOf(5L, 3L, 9L), GymOrder.parse(" 5, x,3,5,0,-2,9 "))
        assertEquals(emptyList(), GymOrder.parse(null))
        assertEquals("5,3,9", GymOrder.serialize(listOf(5, 3, 9)))
    }

    @Test fun favoritesFirstByDefaultKeepingTheRest() {
        val gyms = listOf(gym(1), gym(2), gym(3), gym(4))
        assertEquals(listOf(3L, 4L, 1L, 2L), GymOrder.sort(gyms, emptyList(), setOf(3, 4)).map { it.gymId })
    }

    @Test fun manualOrderWinsAndUnknownIdsAreIgnored() {
        val gyms = listOf(gym(1), gym(2), gym(3), gym(4))
        // 3 es favorito pero el 4 y el 1 se han puesto a mano delante; el 99 no existe.
        assertEquals(listOf(4L, 1L, 3L, 2L), GymOrder.sort(gyms, listOf(4, 99, 1), setOf(3)).map { it.gymId })
    }

    @Test fun moveUpAndDownStopsAtTheEnds() {
        assertEquals(listOf(2L, 1L, 3L), GymOrder.move(listOf(1, 2, 3), 1, -1))
        assertEquals(listOf(1L, 3L, 2L), GymOrder.move(listOf(1, 2, 3), 1, 1))
        assertEquals(listOf(1L, 2L, 3L), GymOrder.move(listOf(1, 2, 3), 0, -1))
        assertEquals(listOf(1L, 2L, 3L), GymOrder.move(listOf(1, 2, 3), 2, 1))
    }

    private val summary = GymSummary(
        sessions = 3, ascents = 7,
        byGym = listOf(
            gym(
                5, 7,
                items = listOf(GymAscent("route:partner:sent:7b", 2), GymAscent("board:kilter:7b", 1), GymAscent("board:kilter:6c", 1), GymAscent("verde", 3)),
                days = listOf(
                    GymSummaryDay("2026-11-18", 4, listOf(GymAscent("route:partner:sent:7b", 1), GymAscent("board:kilter:7b", 1), GymAscent("verde", 2))),
                    GymSummaryDay("2026-11-10", 2, listOf(GymAscent("board:kilter:6c", 1), GymAscent("verde", 1)))
                )
            )
        )
    )

    @Test fun gradesListExcludesColors() {
        assertEquals(listOf("6c", "7b"), AscentEntries.grades(summary.items.ifEmpty { summary.byGym.first().items }))
    }

    @Test fun entriesByGradeNewestFirstWithRemainderAsBefore() {
        val all = AscentEntries.entries(summary, AscentFilter.ALL, setOf("7b"))
        // Con dia: la via y el board del 18; sin dia («antes»): la otra via de 7b que el resumen cuenta pero no trae con fecha.
        assertEquals(listOf("2026-11-18", "2026-11-18", null), all.map { it.date })
        assertEquals(3, all.sumOf { it.count })
    }

    @Test fun entriesRespectCategoryAndMultiSelect() {
        val boards = AscentEntries.entries(summary, AscentFilter.BOARDS, setOf("7b", "6c"))
        assertEquals(listOf("2026-11-18", "2026-11-10"), boards.map { it.date })
        assertEquals(emptyList(), AscentEntries.entries(summary, AscentFilter.ALL, emptySet()))
        assertEquals(emptyList(), AscentEntries.entries(summary, AscentFilter.BLOCKS, setOf("7b")))
    }
}
