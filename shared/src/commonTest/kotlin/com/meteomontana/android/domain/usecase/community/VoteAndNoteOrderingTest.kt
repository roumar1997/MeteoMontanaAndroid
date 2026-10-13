package com.meteomontana.android.domain.usecase.community

import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.usecase.notes.NoteOrdering
import kotlin.test.Test
import kotlin.test.assertEquals

class VoteAndNoteOrderingTest {

    @Test fun `las barras salen por votos y los empates por grado, siempre igual`() {
        val votes = mapOf("7a+" to 2, "6c" to 2, "7a" to 5, "6b" to 1)
        val expected = listOf("7a", "6c", "7a+", "6b")
        assertEquals(expected, VoteOrdering.ordered(votes).map { it.option })
        // Mismo resultado con el mapa en otro orden: no puede depender del orden de inserción.
        assertEquals(expected, VoteOrdering.ordered(votes.entries.reversed().associate { it.key to it.value }).map { it.option })
    }

    @Test fun `las orientaciones empatadas van por nombre`() {
        assertEquals(listOf("N", "NE", "S"), VoteOrdering.ordered(mapOf("S" to 1, "N" to 1, "NE" to 1)).map { it.option })
    }

    private fun note(id: String, up: Int, down: Int, at: String) = Note(
        id = id, schoolId = "s", text = id, author = null, uid = "u",
        createdAt = at, upvotesCount = up, downvotesCount = down
    )

    private val notes = listOf(
        note("a", 5, 1, "2026-10-01"),   // 4
        note("b", 2, 0, "2026-10-03"),   // 2
        note("c", 2, 0, "2026-10-05"),   // 2, más reciente que b
        note("d", 0, 3, "2026-10-02")    // -3
    )

    @Test fun `notas - mas votadas primero y el empate por fecha reciente`() {
        assertEquals(listOf("a", "c", "b", "d"), NoteOrdering.byVotes(notes, mostVotedFirst = true).map { it.id })
    }

    @Test fun `notas - menos votadas primero y el empate sigue siendo la mas reciente`() {
        assertEquals(listOf("d", "c", "b", "a"), NoteOrdering.byVotes(notes, mostVotedFirst = false).map { it.id })
    }
}
