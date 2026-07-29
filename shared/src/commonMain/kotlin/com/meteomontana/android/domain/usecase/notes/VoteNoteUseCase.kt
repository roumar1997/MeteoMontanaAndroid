package com.meteomontana.android.domain.usecase.notes

import com.meteomontana.android.domain.repository.NoteRepository

/** Vota una nota comunitaria (+1/-1/0); devuelve mi voto resultante. */
class VoteNoteUseCase(private val repository: NoteRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(noteId: String, value: Int): Int = repository.voteNote(noteId, value)
}
