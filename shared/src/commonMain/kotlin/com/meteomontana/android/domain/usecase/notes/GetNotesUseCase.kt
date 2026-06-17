package com.meteomontana.android.domain.usecase.notes

import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.repository.NoteRepository

class GetNotesUseCase(private val repository: NoteRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String): List<Note> = repository.getNotes(schoolId)
}
