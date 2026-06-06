package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.Note

interface NoteRepository {
    suspend fun getNotes(schoolId: String): List<Note>
    suspend fun createNote(schoolId: String, text: String): Note
}
