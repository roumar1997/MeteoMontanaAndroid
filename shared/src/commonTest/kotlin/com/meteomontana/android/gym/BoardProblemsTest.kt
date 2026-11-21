package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.AscentEntries
import com.meteomontana.android.domain.model.gym.AscentFilter
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.BoardProblems
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.domain.model.gym.GymSummaryDay
import com.meteomontana.android.domain.model.gym.GymSummaryGym
import com.meteomontana.android.domain.model.gym.NamedBoardProblem
import kotlin.test.Test
import kotlin.test.assertEquals

class BoardProblemsTest {
    private val k6c = "board:kilter:6c:40"

    @Test fun cleanNameTrimsCollapsesAndCuts() {
        assertEquals("Slab Master", BoardProblems.cleanName("  Slab \n  Master "))
        assertEquals(60, BoardProblems.cleanName("x".repeat(100)).length)
        assertEquals("", BoardProblems.cleanName("   "))
    }

    @Test fun addIgnoresEmptyNames() {
        assertEquals(emptyList(), BoardProblems.add(emptyList(), BoardKind.KILTER, "6c", "  "))
        assertEquals(listOf(BoardProblem(BoardKind.KILTER, "6c", "A")), BoardProblems.add(emptyList(), BoardKind.KILTER, "6C", " A "))
    }

    @Test fun pruneKeepsOnlyWhatTheCountsAllow() {
        val ps = listOf(BoardProblem(BoardKind.KILTER, "6c", "A"), BoardProblem(BoardKind.KILTER, "6c", "B"), BoardProblem(BoardKind.TENSION, "6a", "C"))
        assertEquals(listOf("A"), BoardProblems.prune(ps, listOf(GymAscent(k6c, 1), GymAscent("verde", 3))).map { it.name })
        assertEquals(emptyList(), BoardProblems.prune(ps, emptyList()))
    }

    @Test fun namesByKeySpendsNamesInOrder() {
        val ps = listOf(BoardProblem(BoardKind.KILTER, "6c", "A"), BoardProblem(BoardKind.KILTER, "6c", "B"))
        val out = BoardProblems.namesByKey(listOf(GymAscent(k6c, 1), GymAscent("board:kilter:6c", 3)), ps)
        assertEquals(listOf("A"), out[k6c])
        assertEquals(listOf("B"), out["board:kilter:6c"])
    }

    @Test fun searchIsCaseAndAccentInsensitiveAndPerBoard() {
        val all = listOf(
            NamedBoardProblem(1, "2026-11-01", BoardProblem(BoardKind.KILTER, "6c", "La Gran Pared")),
            NamedBoardProblem(1, "2026-11-10", BoardProblem(BoardKind.KILTER, "6b", "Pequeño Dragón")),
            NamedBoardProblem(1, "2026-11-12", BoardProblem(BoardKind.TENSION, "6b", "Dragón"))
        )
        assertEquals(listOf("Pequeño Dragón"), BoardProblems.search(all, BoardKind.KILTER, "PEQUENO").map { it.problem.name })
        assertEquals(listOf("Pequeño Dragón", "La Gran Pared"), BoardProblems.search(all, BoardKind.KILTER, "").map { it.problem.name })
        assertEquals(listOf("Dragón"), BoardProblems.search(all, BoardKind.TENSION, "dragon").map { it.problem.name })
    }

    @Test fun gradeEntriesCarryTheNames() {
        val day = GymSummaryDay("2026-11-18", 2, listOf(GymAscent(k6c, 2)), listOf(BoardProblem(BoardKind.KILTER, "6c", "A")))
        val s = GymSummary(1, 1, 2, "6c", listOf(GymSummaryGym(5, "Chango", 1, 2, "6c", listOf(GymAscent(k6c, 2)), listOf(day))))
        val e = AscentEntries.entries(s, AscentFilter.BOARDS, setOf("6c")).single()
        assertEquals(listOf("A"), e.names)
        assertEquals(2, e.count)
    }
}
