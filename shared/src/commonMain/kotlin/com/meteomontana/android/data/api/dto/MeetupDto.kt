package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeetupDto(
    val id: String,
    val schoolId: String,
    val schoolName: String? = null,
    val name: String,
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
    val joined: Boolean = false
)

@Serializable
data class MeetupMemberDto(
    val uid: String,
    val username: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
)

@Serializable
data class CreateMeetupRequestDto(
    val schoolId: String,
    val name: String,
    val discipline: String? = null,
    val privacy: String,
    val memberLimit: Int? = null,
    val photoUrl: String? = null,
    val days: List<String>
)
