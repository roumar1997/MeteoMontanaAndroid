package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.NoteApi
import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.repository.NoteRepository
import javax.inject.Inject

class RetrofitNoteRepository @Inject constructor(
    private val api: NoteApi
) : NoteRepository {
    override suspend fun getNotes(schoolId: String): List<Note> =
        api.getNotesBySchool(schoolId).map { it.toDomain() }
    override suspend fun createNote(schoolId: String, text: String): Note =
        api.createNote(schoolId, CreateNoteRequest(text)).toDomain()
}
