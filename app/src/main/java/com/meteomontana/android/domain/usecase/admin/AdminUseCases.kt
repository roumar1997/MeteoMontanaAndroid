package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.data.api.AdminApi
import com.meteomontana.android.data.api.dto.AdminLogDto
import com.meteomontana.android.data.api.dto.AdminPushRequest
import com.meteomontana.android.data.api.dto.AdminPushResponse
import com.meteomontana.android.data.api.dto.AdminStatsDto
import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.RejectReason
import com.meteomontana.android.data.api.dto.SubmissionDto
import javax.inject.Inject

class GetAdminStatsUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(): AdminStatsDto = api.stats()
}

class GetPendingSubmissionsUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(): List<SubmissionDto> = api.pendingSubmissions()
}

class GetPendingContributionsUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(): List<ContributionDto> = api.pendingContributions()
}

class GetAdminLogsUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(limit: Int = 100): List<AdminLogDto> = api.logs(limit)
}

class ApproveSubmissionUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(id: String): SubmissionDto = api.approve(id)
}

class RejectSubmissionUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(id: String, reason: String?): SubmissionDto =
        api.reject(id, RejectReason(reason))
}

class ApproveContributionUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(id: String): ContributionDto = api.approveContribution(id)
}

class RejectContributionUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(id: String, reason: String?): ContributionDto =
        api.rejectContribution(id, RejectReason(reason))
}

class SendPushUseCase @Inject constructor(private val api: AdminApi) {
    suspend operator fun invoke(targetUid: String?, title: String, body: String): AdminPushResponse =
        api.sendPush(AdminPushRequest(targetUid, title, body))
}
