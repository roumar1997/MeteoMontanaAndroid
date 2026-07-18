package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorAdminApi
import com.meteomontana.android.data.api.dto.AdminPushRequest
import com.meteomontana.android.data.api.dto.RejectReason
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.MeetupReportDto
import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminPushResult
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.MeetupReport
import com.meteomontana.android.domain.model.Submission
import com.meteomontana.android.domain.repository.AdminRepository

class KtorAdminRepository(private val api: KtorAdminApi) : AdminRepository {

    override suspend fun getStats(): AdminStats = api.stats().toDomain()

    override suspend fun getPendingSubmissions(): List<Submission> =
        api.pendingSubmissions().map { it.toDomain() }

    override suspend fun getPendingContributions(): List<Contribution> =
        api.pendingContributions().map { it.toDomain() }

    override suspend fun getLogs(limit: Int): List<AdminLog> =
        api.logs(limit).map { it.toDomain() }

    override suspend fun approveSubmission(id: String): Submission =
        api.approve(id).toDomain()

    override suspend fun rejectSubmission(id: String, reason: String?): Submission =
        api.reject(id, RejectReason(reason)).toDomain()

    override suspend fun approveContribution(id: String, editedBloquesJson: String?): Contribution =
        api.approveContribution(id, editedBloquesJson).toDomain()

    override suspend fun rejectContribution(id: String, reason: String?): Contribution =
        api.rejectContribution(id, RejectReason(reason)).toDomain()

    override suspend fun sendPush(targetUid: String?, title: String, body: String): AdminPushResult =
        api.sendPush(AdminPushRequest(targetUid, title, body)).toDomain()

    override suspend fun getPendingReports(): List<MeetupReport> =
        api.getPendingReports().map { it.toDomain() }

    override suspend fun resolveReport(id: String, action: String): MeetupReport =
        api.resolveReport(id, action).toDomain()
}

private fun MeetupReportDto.toDomain() = MeetupReport(
    id = id,
    meetupId = meetupId,
    reporterUid = reporterUid,
    reportedUid = reportedUid,
    reason = reason,
    context = context,
    status = status,
    resolvedBy = resolvedBy,
    createdAt = createdAt
)
