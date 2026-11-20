package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReportRequestDto(
    val reportedUid: String? = null,
    val reason: String = "OTHER",    // SPAM | INAPPROPRIATE | HARASSMENT | OTHER
    val context: String? = null
)

@Serializable
data class MeetupAlertDto(
    val enabled: Boolean = false,
    val schoolId: String? = null,   // null = cualquier escuela
    val schoolName: String? = null,
    val daysCsv: String? = null,    // null = cualquier día
    val discipline: String? = null, // BOULDER | ROUTE | BOTH | null = cualquiera
    val privacy: String? = null,    // OPEN | FOLLOWERS | WOMEN | null = cualquiera
    val maxDistanceKm: Int? = null, // null = sin límite
    val userLat: Double? = null,
    val userLon: Double? = null
)

@Serializable
data class SetAlertRequestDto(
    val enabled: Boolean,
    val daysCsv: String? = null,
    val schoolId: String? = null,
    val discipline: String? = null,
    val privacy: String? = null,
    val maxDistanceKm: Int? = null,
    val userLat: Double? = null,
    val userLon: Double? = null
)

@Serializable
data class MeetupDto(
    val id: String,
    /** null en una quedada de rocodromo (V106): las versiones <= 7.2.2 no lo toleran, ver docs/rocodromos/COMPAT.md. */
    val schoolId: String? = null,
    val schoolName: String? = null,
    val schoolLat: Double? = null,
    val schoolLon: Double? = null,
    val name: String,
    val description: String? = null,
    val discipline: String? = null,
    val privacy: String,
    val memberLimit: Int? = null,
    val memberCount: Int,
    val photoUrl: String? = null,
    val creatorUid: String,
    val creatorUsername: String? = null,
    val creatorPhotoUrl: String? = null,
    val conversationId: String,
    val days: List<String>,       // ISO dates
    val lastDay: String,
    val expiresAt: String,        // ISO datetime
    val createdAt: String,
    val members: List<MeetupMemberDto> = emptyList(),
    val joined: Boolean = false,
    /** Rocodromo de la quedada; null si es en una escuela. */
    val gymId: Long? = null,
    /** Nombre del lugar (escuela o rocodromo) ya resuelto por el servidor. */
    val placeName: String? = null,
    val gradeMin: String? = null,
    val gradeMax: String? = null,
    val partnersWanted: Int? = null,
    val requiredGear: List<MeetupGearDto> = emptyList(),
    val gearCoverage: List<MeetupGearCoverageDto> = emptyList()
)

@Serializable
data class MeetupGearDto(val item: String = "", val qty: Int = 0)

@Serializable
data class MeetupGearCoverageDto(
    val item: String = "",
    val required: Int = 0,
    val provided: Int = 0,
    val ok: Boolean = false
)

@Serializable
data class MeetupMemberDto(
    val uid: String,
    val username: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val gearJson: String? = null
)

@Serializable
data class UpdateMeetupRequestDto(
    val description: String? = null
)

@Serializable
data class CreateMeetupRequestDto(
    val schoolId: String? = null,
    val name: String,
    val description: String? = null,
    val discipline: String? = null,
    val privacy: String,
    val memberLimit: Int? = null,
    val photoUrl: String? = null,
    val days: List<String>,
    val gymId: Long? = null,
    val gradeMin: String? = null,
    val gradeMax: String? = null,
    val partnersWanted: Int? = null,
    val requiredGear: List<MeetupGearDto> = emptyList()
)
