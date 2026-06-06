package com.meteomontana.android.domain.usecase.notes

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Note
import javax.inject.Inject

class GetNotesUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(schoolId: String): List<Note> =
        api.getNotesBySchool(schoolId).map { it.toDomain() }
}
