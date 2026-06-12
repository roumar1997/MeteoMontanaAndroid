package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorNoteApi
import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.repository.NoteRepository

class KtorNoteRepository(private val api: KtorNoteApi) : NoteRepository {

    override suspend fun getNotes(schoolId: String): List<Note> =
        api.getNotesBySchool(schoolId).map { it.toDomain() }

    override suspend fun createNote(schoolId: String, text: String, photoUrl: String?): Note =
        api.createNote(schoolId, CreateNoteRequest(text, photoUrl)).toDomain()
}
