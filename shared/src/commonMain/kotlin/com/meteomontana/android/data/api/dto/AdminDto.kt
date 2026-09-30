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
    val submissionsRejected: Long,
    val dailyActiveUsers: Long = 0,
    val weeklyActiveUsers: Long = 0,
    val monthlyActiveUsers: Long = 0,
    val dailyOpensTotal: Long = 0
)

/** Un usuario que abrió la app un día concreto (Álvaro, 2026-09-30). */
@Serializable
data class ActiveUserRowDto(
    val uid: String,
    val username: String? = null,
    val displayName: String? = null,
    val photoPath: String? = null,
    val lastSeen: String? = null,
    val openCount: Int = 1
)

@Serializable
data class DailyActivityDto(
    val totalOpens: Long,
    val users: List<ActiveUserRowDto>
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
