package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class ContributionRequest(
    val type: String,            // PARKING | BOULDER | SECTOR | POSITION_CORRECTION
    val name: String?,
    val lat: Double,
    val lon: Double,
    val notes: String?,
    val description: String?,
    val proposedLat: Double?,    // POSITION_CORRECTION: nueva lat propuesta
    val proposedLon: Double?,    // POSITION_CORRECTION: nueva lon propuesta
    val correctionReason: String?,
    val targetBlockId: String?   // POSITION_CORRECTION: id del bloque a mover (null = la escuela)
)

@JsonClass(generateAdapter = true)
data class ContributionDto(
    val id: String,
    val type: String,
    val status: String,         // PENDING | APPROVED | REJECTED
    val schoolId: String,
    val schoolName: String,
    val name: String?,
    val lat: Double,
    val lon: Double,
    val notes: String?,
    val description: String?,
    val submittedByName: String?,
    val reviewReason: String?,
    val createdAt: String?,
    val reviewedAt: String?
)
