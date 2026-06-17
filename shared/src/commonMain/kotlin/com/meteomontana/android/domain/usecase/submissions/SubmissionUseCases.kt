package com.meteomontana.android.domain.usecase.submissions

import com.meteomontana.android.data.api.dto.SubmitSchoolRequest
import com.meteomontana.android.domain.model.Submission
import com.meteomontana.android.domain.repository.SubmissionRepository

class GetMySubmissionsUseCase(private val repo: SubmissionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<Submission> = repo.getMySubmissions()
}

class SubmitSchoolUseCase(private val repo: SubmissionRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(req: SubmitSchoolRequest): Submission = repo.submitSchool(req)
}
