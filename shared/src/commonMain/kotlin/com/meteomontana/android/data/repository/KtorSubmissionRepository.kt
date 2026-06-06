package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorSubmissionApi
import com.meteomontana.android.data.api.dto.SubmitSchoolRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Submission
import com.meteomontana.android.domain.repository.SubmissionRepository

class KtorSubmissionRepository(private val api: KtorSubmissionApi) : SubmissionRepository {
    override suspend fun submitSchool(req: SubmitSchoolRequest): Submission =
        api.submitSchool(req).toDomain()
    override suspend fun getMySubmissions(): List<Submission> =
        api.getMySubmissions().map { it.toDomain() }
}
