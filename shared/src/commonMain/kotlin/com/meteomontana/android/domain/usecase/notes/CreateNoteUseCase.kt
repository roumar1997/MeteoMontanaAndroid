package com.meteomontana.android.domain.usecase.notes

import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.repository.NoteRepository

class CreateNoteUseCase(private val repository: NoteRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String, text: String, photoUrl: String? = null): Note =
        repository.createNote(schoolId, text, photoUrl)
}
