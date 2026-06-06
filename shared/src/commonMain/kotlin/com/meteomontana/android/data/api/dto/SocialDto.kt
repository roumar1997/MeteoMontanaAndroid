package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PublicProfileDto(
    val uid: String,
    val username: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val bio: String? = null,
    val topGrade: String? = null
)

@Serializable
data class FollowStatusDto(
    val followers: Long,
    val following: Long,
    val iFollowThem: Boolean,
    val theyFollowMe: Boolean
)

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val readAt: String? = null,
    val createdAt: String
)

@Serializable
data class InboxDto(
    val unreadCount: Long,
    val items: List<NotificationDto>
)
