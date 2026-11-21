package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.BoardAngleMemory
import com.meteomontana.android.domain.model.gym.BoardAngles
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.NamedBoardProblem
import com.meteomontana.android.domain.port.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardAnglesTest {
    private class MemStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    @Test
    fun `inclinaciones de cada board`() {
        assertEquals(listOf(20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70), BoardAngles.anglesFor(BoardKind.KILTER))
        assertEquals(BoardAngles.anglesFor(BoardKind.KILTER), BoardAngles.anglesFor(BoardKind.TENSION))
        assertEquals(listOf(25, 40), BoardAngles.anglesFor(BoardKind.MOONBOARD))
        assertEquals(emptyList(), BoardAngles.anglesFor(BoardKind.SYSTEM))
    }

    @Test
    fun `por defecto y ultima usada`() {
        assertEquals(45, BoardAngles.defaultAngle(BoardKind.KILTER))
        assertEquals(25, BoardAngles.defaultAngle(BoardKind.MOONBOARD))
        assertEquals(25, BoardAngles.defaultAngle(BoardKind.TENSION))
        assertNull(BoardAngles.defaultAngle(BoardKind.SYSTEM))
        assertEquals(40, BoardAngles.initial(BoardKind.MOONBOARD, 40))
        assertEquals(25, BoardAngles.initial(BoardKind.MOONBOARD, 45)) // 45 no existe en Moon: vuelve al de por defecto
        assertNull(BoardAngles.initial(BoardKind.SYSTEM, 40))
    }

    @Test
    fun `la memoria recuerda la ultima por board`() {
        val store = MemStore()
        val mem = BoardAngleMemory(store)
        assertEquals(45, mem.initial(BoardKind.KILTER))
        mem.remember(BoardKind.KILTER, 30)
        mem.remember(BoardKind.MOONBOARD, 40)
        mem.remember(BoardKind.MOONBOARD, 99) // invalida: no se guarda
        val again = BoardAngleMemory(store)
        assertEquals(30, again.initial(BoardKind.KILTER))
        assertEquals(40, again.initial(BoardKind.MOONBOARD))
        assertEquals(25, again.initial(BoardKind.TENSION))
    }

    private val items = listOf(
        GymAscent("board:kilter:6c:40", 2), GymAscent("board:kilter:7a:45", 1), GymAscent("board:kilter:6a:40", 3),
        GymAscent("board:kilter:6b", 4), GymAscent("board:moonboard:7a:25", 5)
    )

    @Test
    fun `cuenta y filtra por inclinacion`() {
        assertEquals(listOf(40 to 5, 45 to 1), BoardAngles.counts(items, BoardKind.KILTER))
        assertEquals(listOf(25 to 5), BoardAngles.counts(items, BoardKind.MOONBOARD))
        assertEquals(listOf("board:kilter:6c:40", "board:kilter:6a:40"), BoardAngles.filter(items, BoardKind.KILTER, 40).map { it.key })
        assertEquals(items, BoardAngles.filter(items, BoardKind.KILTER, null))
    }

    @Test
    fun `los nombres salen solo si el grado se hizo en esa inclinacion`() {
        val named = listOf(
            NamedBoardProblem(1, "2026-11-20", BoardProblem(BoardKind.KILTER, "7a", "Alta")),
            NamedBoardProblem(1, "2026-11-19", BoardProblem(BoardKind.KILTER, "6c", "Media"))
        )
        assertEquals(listOf("Alta"), BoardAngles.problemsAt(named, items, BoardKind.KILTER, 45).map { it.problem.name })
        assertEquals(listOf("Media"), BoardAngles.problemsAt(named, items, BoardKind.KILTER, 40).map { it.problem.name })
        assertEquals(2, BoardAngles.problemsAt(named, items, BoardKind.KILTER, null).size)
    }
}
