package com.meteomontana.android.domain.model

data class AdminStats(
    val totalUsers: Long,
    val totalAdmins: Long,
    val totalSchools: Long,
    val totalNotes: Long,
    val submissionsPending: Long,
    val submissionsApproved: Long,
    val submissionsRejected: Long,
    // Usuarios DISTINTOS que abrieron la app hoy / últimos 7 días / últimos
    // 30 días (Álvaro, 2026-09-30) — no aperturas totales, eso es dailyOpensTotal.
    val dailyActiveUsers: Long = 0,
    val weeklyActiveUsers: Long = 0,
    val monthlyActiveUsers: Long = 0,
    // Aperturas TOTALES de hoy (cuenta repetidas: quien entra 5 veces suma 5).
    val dailyOpensTotal: Long = 0
)

/** Un usuario que abrió la app un día concreto, y cuántas veces ese día
 *  (Álvaro, 2026-09-30). */
data class ActiveUserRow(
    val uid: String,
    val username: String?,
    val displayName: String?,
    val photoPath: String?,
    val lastSeen: String?,
    val openCount: Int
)

/** Respuesta de "quién entró tal día": la lista + el total de aperturas de
 *  ESE día (no usuarios distintos). */
data class DailyActivity(
    val totalOpens: Long,
    val users: List<ActiveUserRow>
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

data class MeetupReport(
    val id: String,
    val meetupId: String,
    val reporterUid: String,
    val reportedUid: String?,
    val reason: String,
    val context: String?,
    val status: String,        // PENDING | RESOLVED | DISMISSED
    val resolvedBy: String?,
    val createdAt: String
)

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
    // Resueltos al leer, no persistidos (Álvaro, 2026-09-30: "saber quién la
    // hizo, quién la aprobó").
    val submittedByName: String? = null,
    val submittedByPhotoPath: String? = null,
    val reviewedByUid: String?,
    val reviewedByName: String? = null,
    val reviewReason: String?,
    val createdSchoolId: String?,
    val createdAt: String,
    val reviewedAt: String?
)
