package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubmissionDto(
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
    val status: String,  // PENDING / APPROVED / REJECTED
    val submittedByUid: String,
    val reviewedByUid: String?,
    val reviewReason: String?,
    val createdSchoolId: String?,
    val createdAt: String,
    val reviewedAt: String?
)

@JsonClass(generateAdapter = true)
data class SubmitSchoolRequest(
    val name: String,
    val region: String?,
    val style: String?,
    val rockType: String?,
    val lat: Double,
    val lon: Double,
    val location: String?,
    val source: String?,
    val notes: String?
)
