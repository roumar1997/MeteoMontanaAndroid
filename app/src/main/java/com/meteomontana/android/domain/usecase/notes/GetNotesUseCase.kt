package com.meteomontana.android.domain.usecase.notes

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.NoteDto
import javax.inject.Inject

class GetNotesUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(schoolId: String): List<NoteDto> = api.getNotesBySchool(schoolId)
}
