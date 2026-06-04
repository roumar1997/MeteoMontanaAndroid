package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PublicProfileDto(
    val uid: String,
    val username: String?,
    val displayName: String?,
    val photoUrl: String?,
    val bio: String?,
    val topGrade: String?
)

@JsonClass(generateAdapter = true)
data class FollowStatusDto(
    val followers: Long,
    val following: Long,
    val iFollowThem: Boolean,
    val theyFollowMe: Boolean
)

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String?,
    val targetType: String?,
    val targetId: String?,
    val readAt: String?,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class InboxDto(
    val unreadCount: Long,
    val items: List<NotificationDto>
)
