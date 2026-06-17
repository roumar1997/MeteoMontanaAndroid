package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminPushResult
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.Submission
import com.meteomontana.android.domain.repository.AdminRepository

class GetAdminStatsUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): AdminStats = repository.getStats()
}

class GetPendingSubmissionsUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<Submission> = repository.getPendingSubmissions()
}

class GetPendingContributionsUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<Contribution> = repository.getPendingContributions()
}

class GetAdminLogsUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(limit: Int = 100): List<AdminLog> = repository.getLogs(limit)
}

class ApproveSubmissionUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String): Submission = repository.approveSubmission(id)
}

class RejectSubmissionUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String, reason: String?): Submission =
        repository.rejectSubmission(id, reason)
}

class ApproveContributionUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String): Contribution = repository.approveContribution(id)
}

class RejectContributionUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String, reason: String?): Contribution =
        repository.rejectContribution(id, reason)
}

class SendPushUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(targetUid: String?, title: String, body: String): AdminPushResult =
        repository.sendPush(targetUid, title, body)
}
