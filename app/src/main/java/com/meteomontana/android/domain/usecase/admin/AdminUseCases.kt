package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.data.api.AdminApi
import com.meteomontana.android.data.api.dto.AdminPushRequest
import com.meteomontana.android.data.api.dto.RejectReason
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminPushResult
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.Submission
import javax.inject.Inject

class GetAdminStatsUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(): AdminStats = api.stats().toDomain()
}

class GetPendingSubmissionsUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(): List<Submission> = api.pendingSubmissions().map { it.toDomain() }
}

class GetPendingContributionsUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(): List<Contribution> = api.pendingContributions().map { it.toDomain() }
}

class GetAdminLogsUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(limit: Int = 100): List<AdminLog> = api.logs(limit).map { it.toDomain() }
}

class ApproveSubmissionUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(id: String): Submission = api.approve(id).toDomain()
}

class RejectSubmissionUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(id: String, reason: String?): Submission =
        api.reject(id, RejectReason(reason)).toDomain()
}

class ApproveContributionUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(id: String): Contribution = api.approveContribution(id).toDomain()
}

class RejectContributionUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(id: String, reason: String?): Contribution =
        api.rejectContribution(id, RejectReason(reason)).toDomain()
}

class SendPushUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(targetUid: String?, title: String, body: String): AdminPushResult =
        api.sendPush(AdminPushRequest(targetUid, title, body)).toDomain()
}
