package com.meteomontana.android.domain.model

data class Meetup(
    val id: String,
    val schoolId: String,
    val schoolName: String?,
    val name: String,
    val discipline: String?,    // BOULDER | ROUTE | BOTH | null
    val privacy: String,        // OPEN | FOLLOWERS | WOMEN
    val memberLimit: Int?,
    val memberCount: Int,
    val photoUrl: String?,
    val creatorUid: String,
    val creatorUsername: String?,
    val creatorPhotoUrl: String?,
    val conversationId: String,
    val days: List<String>,     // ISO dates "2026-07-05"
    val lastDay: String,
    val expiresAt: Long,        // epoch millis
    val createdAt: Long,
    val members: List<MeetupMember>,
    val joined: Boolean
) {
    val isFull: Boolean get() = memberLimit != null && memberCount >= memberLimit
}

data class MeetupMember(
    val uid: String,
    val username: String?,
    val displayName: String?,
    val photoUrl: String?
)

data class CreateMeetupRequest(
    val schoolId: String,
    val name: String,
    val discipline: String?,    // BOULDER | ROUTE | BOTH | null
    val privacy: String,
    val memberLimit: Int?,
    val photoUrl: String?,
    val days: List<String>      // ISO dates
)
