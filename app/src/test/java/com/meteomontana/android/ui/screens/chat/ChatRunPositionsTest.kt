package com.meteomontana.android.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRunPositionsTest {
    @Test
    fun nameOnFirstAvatarOnLastOfEachRun() {
        val p = chatRunPositions(listOf("a", "a", "a", "b", "a"))
        assertEquals(listOf(true, false, false, true, true), p.map { it.first })
        assertEquals(listOf(false, false, true, true, true), p.map { it.last })
    }

    @Test
    fun dayBreakSplitsRun() {
        val p = chatRunPositions(listOf("a", "a")) { it == 1 }
        assertEquals(ChatRunPos(first = true, last = true), p[0])
        assertEquals(ChatRunPos(first = true, last = true), p[1])
    }
}
