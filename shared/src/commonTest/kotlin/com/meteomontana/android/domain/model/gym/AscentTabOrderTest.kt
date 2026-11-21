package com.meteomontana.android.domain.model.gym

import kotlin.test.Test
import kotlin.test.assertEquals

class AscentTabOrderTest {
    @Test
    fun brokenOrMissingTextFallsBackToDefault() {
        assertEquals(AscentTabOrder.default, AscentTabOrder.parse(null))
        assertEquals(AscentTabOrder.default, AscentTabOrder.parse("nonsense"))
    }

    @Test
    fun savedOrderIsKeptAndMissingOnesAppended() {
        assertEquals(listOf(AscentFilter.BOARDS, AscentFilter.BLOCKS, AscentFilter.ROUTES), AscentTabOrder.parse("BOARDS"))
        assertEquals(listOf(AscentFilter.ROUTES, AscentFilter.BOARDS, AscentFilter.BLOCKS), AscentTabOrder.parse("ROUTES,BOARDS,BLOCKS,ROUTES"))
    }

    @Test
    fun moveSwapsNeighboursAndIgnoresEdges() {
        val moved = AscentTabOrder.move(AscentTabOrder.default, 2, -1)
        assertEquals(listOf(AscentFilter.BLOCKS, AscentFilter.BOARDS, AscentFilter.ROUTES), moved)
        assertEquals(AscentTabOrder.default, AscentTabOrder.move(AscentTabOrder.default, 0, -1))
        assertEquals(moved, AscentTabOrder.parse(AscentTabOrder.serialize(moved)))
    }

    @Test
    fun allTabAlwaysFirst() {
        assertEquals(AscentFilter.ALL, AscentTabOrder.tabs(listOf(AscentFilter.BOARDS, AscentFilter.BLOCKS, AscentFilter.ROUTES)).first())
    }
}
