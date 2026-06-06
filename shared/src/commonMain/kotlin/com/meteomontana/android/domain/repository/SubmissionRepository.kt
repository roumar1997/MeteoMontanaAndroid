package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.SubmitSchoolRequest
import com.meteomontana.android.domain.model.Submission

interface SubmissionRepository {
    suspend fun submitSchool(req: SubmitSchoolRequest): Submission
    suspend fun getMySubmissions(): List<Submission>
}
