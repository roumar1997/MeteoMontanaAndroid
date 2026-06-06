package com.meteomontana.android.domain.usecase.notes

import com.meteomontana.android.data.api.NoteApi
import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Note
import javax.inject.Inject

class CreateNoteUseCase @Inject constructor(private val api: NoteApi) {
    suspend operator fun invoke(schoolId: String, text: String): Note =
        api.createNote(schoolId, CreateNoteRequest(text)).toDomain()
}
