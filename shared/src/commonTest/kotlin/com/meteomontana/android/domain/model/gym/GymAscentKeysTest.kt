package com.meteomontana.android.domain.model.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymAscentKeysTest {
    @Test fun maxGradeOfAList() {
        fun a(k: String) = GymAscent(k, 1)
        assertEquals("7a", GymBreakdown.maxGrade(listOf(a("board:kilter:6c:40"), a("board:kilter:6b+/7a"), a("board:kilter:6c+"))))
        assertEquals(null, GymBreakdown.maxGrade(emptyList()))
        assertEquals(null, GymBreakdown.maxGrade(listOf(a("verde"))))
    }

    @Test fun routeKeysRoundTrip() {
        val k = GymAscentKeys.route(RouteStyle.PARTNER, project = false, grade = "6A+")
        assertEquals("route:partner:sent:6a+", k)
        assertEquals(AscentKey.Route(RouteStyle.PARTNER, false, "6a+"), GymAscentKeys.parse(k))
        assertEquals(AscentKey.Route(RouteStyle.SOLO, true, "7a"), GymAscentKeys.parse("route:solo:project:7a"))
    }

    @Test fun boardKeysRoundTrip() {
        assertEquals("board:kilter:6c:40", GymAscentKeys.board(BoardKind.KILTER, "6C", 40))
        assertEquals(AscentKey.Board(BoardKind.MOONBOARD, "7a", null), GymAscentKeys.parse("board:moonboard:7a"))
    }

    @Test fun anythingElseIsABlock() {
        assertTrue(GymAscentKeys.parse("verde") is AscentKey.Block)
        assertTrue(GymAscentKeys.parse("6a+") is AscentKey.Block)
        assertTrue(GymAscentKeys.parse("route:duo:sent:6a") is AscentKey.Block)
    }

    @Test fun splitsAndSummarisesByKind() {
        val all = listOf(
            GymAscent("verde", 3), GymAscent("route:solo:sent:6b", 1), GymAscent("route:partner:sent:6a+", 2),
            GymAscent("board:kilter:6c", 4), GymAscent("board:tension:6a", 1)
        )
        assertEquals(listOf("verde"), GymAscentKeys.blocks(all).map { it.key })
        assertEquals(listOf("board:kilter:6c"), GymAscentKeys.boards(all, BoardKind.KILTER).map { it.key })
        assertEquals(listOf("6a+", "6b"), GymAscentKeys.routeSummary(all).map { it.first })
    }
}

class GymAscentKeysDoubleGradeTest {
    @Test fun contiguousGradesMakeADoubleAndOthersRestart() {
        var sel = GymAscentKeys.toggleGrade(emptyList(), "6B+")
        assertEquals(listOf("6b+"), sel)
        sel = GymAscentKeys.toggleGrade(sel, "6c")
        assertEquals("6b+/6c", GymAscentKeys.gradeOf(sel))
        sel = GymAscentKeys.toggleGrade(sel, "7a")
        assertEquals(listOf("7a"), sel)
        assertEquals(listOf("6a"), GymAscentKeys.toggleGrade(listOf("6a", "6a+"), "6a+"))
        assertEquals(listOf("6b"), GymAscentKeys.toggleGrade(listOf("6a"), "6b"))
    }

    @Test fun fivePlusAndSixAAreNotNeighboursForTheServer() {
        assertEquals(false, GymAscentKeys.areContiguous("5+", "6a"))
        assertEquals(true, GymAscentKeys.areContiguous("5", "5+"))
    }

    @Test fun doubleGradeKeyRoundTripsAndSortsByBase() {
        val key = GymAscentKeys.route(RouteStyle.PARTNER, false, "6b+/6c")
        assertEquals(AscentKey.Route(RouteStyle.PARTNER, false, "6b+/6c"), GymAscentKeys.parse(key))
        assertEquals("6b+", GymAscentKeys.baseGrade("6b+/6c"))
        val all = listOf(GymAscent("route:solo:sent:7a", 1), GymAscent(key, 2))
        assertEquals(listOf("6b+/6c", "7a"), GymAscentKeys.routeSummary(all).map { it.first })
    }
}
