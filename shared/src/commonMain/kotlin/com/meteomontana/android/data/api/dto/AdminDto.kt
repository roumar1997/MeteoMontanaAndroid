package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminStatsDto(
    val totalUsers: Long,
    val totalAdmins: Long,
    val totalSchools: Long,
    val totalNotes: Long,
    val submissionsPending: Long,
    val submissionsApproved: Long,
    val submissionsRejected: Long
)

@Serializable
data class AdminLogDto(
    val id: String,
    val actorUid: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val details: String? = null,
    val createdAt: String
)

@Serializable
data class AdminPushRequest(
    val targetUid: String? = null,
    val title: String,
    val body: String
)

@Serializable
data class AdminPushResponse(
    val sent: Int,
    val recipients: Int
)

@Serializable
data class RejectReason(val reason: String? = null)
