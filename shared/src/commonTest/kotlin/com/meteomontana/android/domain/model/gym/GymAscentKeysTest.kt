package com.meteomontana.android.domain.model.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymAscentKeysTest {
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
