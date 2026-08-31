package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.AdminNoteRowDto
import com.meteomontana.android.data.api.AdminSuggestionRowDto
import com.meteomontana.android.data.api.AdminUserRowDto
import com.meteomontana.android.data.api.ContentReportDto
import com.meteomontana.android.data.api.KtorModerationApi
import com.meteomontana.android.data.api.ModActionRowDto
import com.meteomontana.android.data.api.ModReportRowDto
import com.meteomontana.android.data.api.UserModerationDto
import com.meteomontana.android.domain.model.AdminNoteRow
import com.meteomontana.android.domain.model.AdminSuggestionRow
import com.meteomontana.android.domain.model.AdminUserRow
import com.meteomontana.android.domain.model.ContentReport
import com.meteomontana.android.domain.model.ModActionRow
import com.meteomontana.android.domain.model.ModReportRow
import com.meteomontana.android.domain.model.UserModeration
import com.meteomontana.android.domain.repository.ModerationRepository

class KtorModerationRepository(private val api: KtorModerationApi) : ModerationRepository {
    override suspend fun getContentReports(): List<ContentReport> =
        api.getContentReports().map { it.toDomain() }

    override suspend fun resolveContentReport(id: String, action: String): ContentReport? =
        api.resolveContentReport(id, action)?.toDomain()

    override suspend fun getAdminUsers(): List<AdminUserRow> = api.getAdminUsers().map { it.toDomain() }

    override suspend fun getAdminNotes(): List<AdminNoteRow> = api.getAdminNotes().map { it.toDomain() }

    override suspend fun getAdminSuggestions(): List<AdminSuggestionRow> =
        api.getAdminSuggestions().map { it.toDomain() }

    override suspend fun respondToSuggestion(id: String, resolved: Boolean?, reply: String?): AdminSuggestionRow? =
        api.respondToSuggestion(id, resolved, reply)?.toDomain()

    override suspend fun getUserModeration(uid: String): UserModeration? =
        api.getUserModeration(uid)?.toDomain()

    override suspend fun warnUser(uid: String, reason: String?): UserModeration? =
        api.warnUser(uid, reason)?.toDomain()

    override suspend fun suspendUser(uid: String, days: Int, reason: String?): UserModeration? =
        api.suspendUser(uid, days, reason)?.toDomain()

    override suspend fun banUser(uid: String, reason: String?): UserModeration? =
        api.banUser2(uid, reason)?.toDomain()

    override suspend fun unbanUser(uid: String, reason: String?): UserModeration? =
        api.unbanUser2(uid, reason)?.toDomain()
}

private fun ContentReportDto.toDomain() = ContentReport(
    id = id, targetType = targetType, targetId = targetId, reason = reason, snapshot = snapshot,
    authorUid = authorUid, reporterUid = reporterUid, status = status, resolution = resolution,
    createdAt = createdAt
)

private fun AdminUserRowDto.toDomain() = AdminUserRow(
    uid = uid, username = username, displayName = displayName, isAdmin = isAdmin, createdAt = createdAt
)

private fun AdminNoteRowDto.toDomain() = AdminNoteRow(
    id = id, schoolId = schoolId, author = author, uid = uid, text = text, createdAt = createdAt
)

private fun AdminSuggestionRowDto.toDomain() = AdminSuggestionRow(
    id = id, uid = uid, email = email, displayName = displayName, message = message,
    platform = platform, appVersion = appVersion, createdAt = createdAt,
    resolved = resolved, adminReply = adminReply, repliedAt = repliedAt
)

private fun UserModerationDto.toDomain() = UserModeration(
    uid = uid, username = username, displayName = displayName, banned = banned,
    suspendedUntil = suspendedUntil, warnings = warnings, reportCount = reportCount,
    reports = reports.map { it.toDomain() }, actions = actions.map { it.toDomain() }
)

private fun ModReportRowDto.toDomain() = ModReportRow(
    type = type, reason = reason, snapshot = snapshot, createdAt = createdAt
)

private fun ModActionRowDto.toDomain() = ModActionRow(
    action = action, reason = reason, snapshot = snapshot, createdAt = createdAt
)
