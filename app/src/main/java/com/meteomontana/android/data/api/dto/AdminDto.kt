package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AdminStatsDto(
    val totalUsers: Long,
    val totalAdmins: Long,
    val totalSchools: Long,
    val totalNotes: Long,
    val submissionsPending: Long,
    val submissionsApproved: Long,
    val submissionsRejected: Long
)

@JsonClass(generateAdapter = true)
data class AdminLogDto(
    val id: String,
    val actorUid: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val details: String?,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class AdminPushRequest(
    val targetUid: String?,
    val title: String,
    val body: String
)

@JsonClass(generateAdapter = true)
data class AdminPushResponse(
    val sent: Int,
    val recipients: Int
)

@JsonClass(generateAdapter = true)
data class RejectReason(val reason: String?)
