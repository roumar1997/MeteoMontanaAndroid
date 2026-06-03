package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass

/** Respuesta de GET /api/me. */
@JsonClass(generateAdapter = true)
data class PrivateProfileDto(
    val uid: String,
    val email: String?,
    val username: String?,
    val displayName: String?,
    val photoUrl: String?,
    val bio: String?,
    val topGrade: String?,
    val isPublic: Boolean,
    val isAdmin: Boolean,
    val isPremium: Boolean
)
