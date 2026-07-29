package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.Note

interface NoteRepository {
    suspend fun getNotes(schoolId: String): List<Note>
    suspend fun createNote(schoolId: String, text: String, photoUrl: String? = null): Note
    /** Vota una nota (+1/-1/0); devuelve mi voto resultante. */
    suspend fun voteNote(noteId: String, value: Int): Int
}
