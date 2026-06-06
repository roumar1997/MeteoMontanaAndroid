package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubmissionDto(
    val id: String,
    val proposedName: String,
    val proposedRegion: String? = null,
    val proposedStyle: String? = null,
    val proposedRockType: String? = null,
    val proposedLat: Double,
    val proposedLon: Double,
    val proposedLocation: String? = null,
    val proposedSource: String? = null,
    val notes: String? = null,
    val status: String,
    val submittedByUid: String,
    val reviewedByUid: String? = null,
    val reviewReason: String? = null,
    val createdSchoolId: String? = null,
    val createdAt: String,
    val reviewedAt: String? = null
)

@Serializable
data class SubmitSchoolRequest(
    val name: String,
    val region: String? = null,
    val style: String? = null,
    val rockType: String? = null,
    val lat: Double,
    val lon: Double,
    val location: String? = null,
    val source: String? = null,
    val notes: String? = null
)
