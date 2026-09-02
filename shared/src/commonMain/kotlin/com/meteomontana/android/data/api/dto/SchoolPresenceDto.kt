package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.SchoolPresence
import kotlinx.serialization.Serializable

@Serializable
data class SchoolPresenceDto(
    val uid: String,
    val username: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val expiresAt: Long
)

fun SchoolPresenceDto.toDomain() = SchoolPresence(
    uid = uid,
    username = username,
    displayName = displayName,
    photoUrl = photoUrl,
    expiresAt = expiresAt
)
