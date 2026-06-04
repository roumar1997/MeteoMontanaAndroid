package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass

/** Body de PUT /api/me. Campos null = no cambia. */
@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val topGrade: String? = null,
    val isPublic: Boolean? = null
)
