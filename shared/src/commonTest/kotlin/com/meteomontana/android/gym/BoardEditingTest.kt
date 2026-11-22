package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.BoardEditing
import com.meteomontana.android.domain.model.gym.BoardEntry
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardLog
import com.meteomontana.android.domain.model.gym.BoardLogging
import com.meteomontana.android.domain.model.gym.GymSession
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Edit and delete a logged board block (§26). */
class BoardEditingTest {
    private val k = BoardKind.KILTER
    private val empty = BoardLog(emptyList(), emptyList())
    private fun named(reps: Int = 2, attempts: Int? = 3) = BoardEntry(0, "2026-11-20", k, "6c", "Fanjulandia", 40, reps, attempts)

    @Test fun deletingANamedBlockLeavesTheRestOfTheDay() {
        var log = BoardLogging.add(empty, k, "6c", 40, "Fanjulandia", 2, 3)
        log = BoardLogging.add(log, k, "6c", 40, "", 1, null)
        val out = BoardEditing.remove(log, named())
        assertEquals(1, out.ascents.single().count)
        assertTrue(out.problems.isEmpty())
    }

    @Test fun editingChangesGradeAngleNameAttemptsAndReps() {
        val log = BoardLogging.add(empty, k, "6c", 40, "Fanjulandia", 2, 3)
        val out = BoardEditing.replace(log, named(), "7a", 45, "Fanjulandia bis", 3, 1)
        assertEquals("board:kilter:7a:45", out.ascents.single().key)
        assertEquals(3, out.ascents.single().count)
        assertEquals(List(3) { "Fanjulandia bis" }, out.problems.map { it.name })
        assertEquals(1, out.problems.first().attempts)
    }

    @Test fun unnamedEntryOnlyRemovesTheUnnamedReps() {
        var log = BoardLogging.add(empty, k, "6c", 40, "Fanjulandia", 1, null)
        log = BoardLogging.add(log, k, "6c", 40, "", 2, 4)
        val out = BoardEditing.remove(log, BoardEntry(0, "d", k, "6c", null, null, 2, 4))
        assertEquals(1, out.ascents.single().count)
        assertEquals(null, out.ascents.single().attempts)
        assertEquals(1, out.problems.size)
    }

    @Test fun angleIsTakenFromTheDayWhenThereIsOnlyOne() {
        val log = BoardLogging.add(empty, k, "6c", 40, "", 1, null)
        val s = GymSession(GymSession.NO_GYM, LocalDate(2026, 11, 20), ascents = log.ascents)
        assertEquals(40, BoardEditing.withAngle(named().copy(angle = null), s).angle)
        assertEquals(listOf("6B+", "6C").map { it.lowercase() }, BoardEntry(0, "d", k, "6b+/6c", null, null, 1, null).gridGrades.map { it.lowercase() })
    }

    @Test fun anEntryThatIsNotThereIsNotContained() {
        assertFalse(BoardEditing.contains(empty, named()))
        assertTrue(BoardEditing.contains(BoardLogging.add(empty, k, "6c", 40, "Fanjulandia", 2, 3), named()))
    }
}
