package com.meteomontana.android.domain.model.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardGradesTest {
    private fun row(grade: String) = BoardBlockRow(BoardKind.KILTER, grade, null, 1, "2026-11-22", listOf("2026-11-22"), null, emptyList())

    @Test fun toggleSelectsAndClears() {
        assertEquals("7a", BoardGrades.toggle(null, "7a"))
        assertNull(BoardGrades.toggle("7a", "7A"))
        assertEquals("6b", BoardGrades.toggle("7a", "6b"))
    }

    @Test fun filterKeepsOnlyThatBaseGrade() {
        val rows = listOf(row("7a"), row("6b+/6c"), row("6b+"))
        assertEquals(3, BoardGrades.filter(rows, null).size)
        assertEquals(2, BoardGrades.filter(rows, "6B+").size)
        assertEquals(listOf("7a"), BoardGrades.filter(rows, "7a").map { it.grade })
    }

    @Test fun slicesSharesAndTop() {
        val items = listOf(GymAscent("board:kilter:6a+:45", 1), GymAscent("board:kilter:7b+:45", 3))
        val slices = BoardGrades.slices(items)
        assertEquals(2, slices.size)
        assertEquals(0.75f, slices.last().share)
        assertEquals(slices.last().grade, BoardGrades.top(slices))
        assertEquals(3, BoardGrades.maxCount(slices))
    }

    @Test fun emptyItemsGiveNoSlices() {
        assertEquals(emptyList(), BoardGrades.slices(emptyList()))
        assertNull(BoardGrades.top(emptyList()))
        assertEquals(1, BoardGrades.maxCount(emptyList()))
    }
}
