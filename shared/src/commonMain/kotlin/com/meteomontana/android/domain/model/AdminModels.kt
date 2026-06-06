package com.meteomontana.android.domain.model

data class AdminStats(
    val totalUsers: Long,
    val totalAdmins: Long,
    val totalSchools: Long,
    val totalNotes: Long,
    val submissionsPending: Long,
    val submissionsApproved: Long,
    val submissionsRejected: Long
)

data class AdminLog(
    val id: String,
    val actorUid: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val details: String?,
    val createdAt: String
)

data class AdminPushResult(val sent: Int, val recipients: Int)

data class Submission(
    val id: String,
    val proposedName: String,
    val proposedRegion: String?,
    val proposedStyle: String?,
    val proposedRockType: String?,
    val proposedLat: Double,
    val proposedLon: Double,
    val proposedLocation: String?,
    val proposedSource: String?,
    val notes: String?,
    val status: String,
    val submittedByUid: String,
    val reviewedByUid: String?,
    val reviewReason: String?,
    val createdSchoolId: String?,
    val createdAt: String,
    val reviewedAt: String?
)
