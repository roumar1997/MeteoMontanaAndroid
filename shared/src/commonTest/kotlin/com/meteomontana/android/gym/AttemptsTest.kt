package com.meteomontana.android.gym

import com.meteomontana.android.data.api.dto.GymSessionDto
import com.meteomontana.android.data.api.dto.GymSummaryDto
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDomainOrNull
import com.meteomontana.android.domain.model.gym.Attempts
import com.meteomontana.android.domain.model.gym.AttemptsStyleLink
import com.meteomontana.android.domain.model.gym.BoardAscentRow
import com.meteomontana.android.domain.model.gym.BoardBlocks
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardLog
import com.meteomontana.android.domain.model.gym.BoardLogging
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.domain.model.gym.NamedBoardProblem
import com.meteomontana.android.domain.model.gym.StyleAttempts
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Intentos opcionales (1 = flash): enlace con «Al flash», registro de boards, lista unica con y sin nombre y compatibilidad de DTOs. */
class AttemptsTest {

    @Test fun nothingIsSelectedByDefaultAndOnlyOneToNinetyNineIsValid() {
        assertNull(Attempts.clean(null)); assertNull(Attempts.clean(0)); assertNull(Attempts.clean(100))
        assertEquals(30, Attempts.clean(30)); assertTrue(Attempts.isFlash(1)); assertFalse(Attempts.isFlash(2))
        assertTrue(Attempts.isFivePlus(7)); assertFalse(Attempts.isFivePlus(4)); assertEquals(2, Attempts.best(listOf(null, 5, 2)))
    }

    @Test fun tappingOneSelectsFlashAndSelectingFlashSetsOne() {
        val none = StyleAttempts(false, null)
        assertEquals(StyleAttempts(true, 1), AttemptsStyleLink.pick(none, 1))
        assertEquals(StyleAttempts(true, 1), AttemptsStyleLink.setFlash(none, true))
        assertEquals(StyleAttempts(false, 3), AttemptsStyleLink.pick(StyleAttempts(true, 1), 3))
        assertEquals(StyleAttempts(false, null), AttemptsStyleLink.setFlash(StyleAttempts(true, 1), false))
        assertEquals(StyleAttempts(false, null), AttemptsStyleLink.pick(StyleAttempts(true, 1), null))
    }

    @Test fun unnamedAttemptsGoOnTheKeyAndNamedOnesOnEachRepetition() {
        val empty = BoardLog(emptyList(), emptyList())
        val unnamed = BoardLogging.add(empty, BoardKind.KILTER, "6c", 40, "", 2, 3)
        assertEquals(3, unnamed.ascents.single().attempts); assertTrue(unnamed.problems.isEmpty())
        val better = BoardLogging.add(unnamed, BoardKind.KILTER, "6c", 40, "", 1, 1)
        assertEquals(1, better.ascents.single().attempts); assertEquals(3, better.ascents.single().count)
        val named = BoardLogging.add(empty, BoardKind.KILTER, "7b", 30, "Fanjulandia", 2, 1)
        assertNull(named.ascents.single().attempts); assertEquals(listOf(1, 1), named.problems.map { it.attempts })
        val noAttempts = BoardLogging.add(empty, BoardKind.KILTER, "6c", 40, "", 1, null)
        assertNull(noAttempts.ascents.single().attempts)
    }

    private fun summary(vararg named: NamedBoardProblem, rows: List<BoardAscentRow>) =
        GymSummary(boardProblems = named.toList(), boardAscents = rows, items = rows.groupBy { it.key }.map { (k, l) -> com.meteomontana.android.domain.model.gym.GymAscent(k, l.sumOf { it.count }) })

    @Test fun oneListMergesNamedAndUnnamedSortedByGradeWithBestAttempts() {
        val fan = BoardProblem(BoardKind.KILTER, "7b", "Fanjulandia", 1)
        val s = summary(
            NamedBoardProblem(5, "2026-11-21", fan), NamedBoardProblem(5, "2026-11-19", fan.copy(attempts = 4)),
            rows = listOf(
                BoardAscentRow(5, "2026-11-21", "board:kilter:7b:30", 3), BoardAscentRow(5, "2026-11-19", "board:kilter:7b:30", 2),
                BoardAscentRow(0, "2026-11-21", "board:kilter:6c:30", 1, 2), BoardAscentRow(0, "2026-11-20", "board:kilter:6a+:30", 2)
            )
        )
        val rows = BoardBlocks.rows(s, BoardKind.KILTER, null, "")
        assertEquals(listOf("7b", "7b", "6c", "6a+"), rows.map { it.grade })
        val named = rows.first { it.isNamed }
        assertEquals(2, named.reps); assertTrue(named.isFlash)
        val unnamed7b = rows.first { it.grade == "7b" && !it.isNamed }
        assertEquals(3, unnamed7b.reps)
        assertEquals(2, rows.first { it.grade == "6c" }.bestAttempts); assertFalse(rows.first { it.grade == "6c" }.isFlash)
        assertNull(rows.first { it.grade == "6a+" }.bestAttempts)
        assertEquals(8, BoardBlocks.total(rows))
        // buscar solo encuentra los que tienen nombre; el filtro de inclinacion deja solo esa
        assertEquals(1, BoardBlocks.rows(s, BoardKind.KILTER, null, "fanju").size)
        assertTrue(BoardBlocks.rows(s, BoardKind.KILTER, 45, "").isEmpty())
    }

    @Test fun namedDayKeepsItsOwnBestAttempts() {
        val s = summary(
            NamedBoardProblem(5, "2026-11-21", BoardProblem(BoardKind.KILTER, "7b", "X", 2)), NamedBoardProblem(5, "2026-11-21", BoardProblem(BoardKind.KILTER, "7b", "X", 1)),
            rows = listOf(BoardAscentRow(5, "2026-11-21", "board:kilter:7b:30", 2))
        )
        val day = BoardBlocks.rows(s, BoardKind.KILTER, null, "").single().days.single()
        assertEquals(2, day.reps); assertEquals(1, day.attempts)
    }

    @Test fun oldServerJsonWithoutAttemptsStillParsesAndNewOneKeepsThem() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<GymSessionDto>("""{"id":1,"gymId":5,"date":"2026-11-19","ascents":[{"key":"verde","count":5}],"boardProblems":[{"board":"kilter","grade":"6c","name":"Slab"}]}""").toDomainOrNull()!!
        assertNull(old.ascents.single().attempts); assertNull(old.boardProblems.single().attempts)
        val new = json.decodeFromString<GymSessionDto>("""{"id":1,"gymId":5,"date":"2026-11-19","ascents":[{"key":"board:kilter:6c:40","count":2,"attempts":1}],"boardProblems":[{"board":"kilter","grade":"6c","name":"Slab","attempts":3}]}""").toDomainOrNull()!!
        assertEquals(1, new.ascents.single().attempts); assertEquals(3, new.boardProblems.single().attempts)
        val sum = json.decodeFromString<GymSummaryDto>("""{"boardAscents":[{"gymId":0,"date":"2026-11-21","key":"board:kilter:6c:40","count":2,"attempts":1}]}""").toDomain()
        assertEquals(1, sum.boardAscents.single().attempts)
        assertTrue(json.decodeFromString<GymSummaryDto>("""{}""").toDomain().boardAscents.isEmpty())
    }

    @Test fun systemBoardIsHidden() {
        assertFalse(BoardKind.selectable.contains(BoardKind.SYSTEM)); assertEquals(3, BoardKind.selectable.size)
    }
}
