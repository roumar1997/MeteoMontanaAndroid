package com.meteomontana.android.domain.model

data class PrivateProfile(
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

data class PublicProfile(
    val uid: String,
    val username: String?,
    val displayName: String?,
    val photoUrl: String?,
    val bio: String?,
    val topGrade: String?
)

data class FollowStatus(
    val followers: Long,
    val following: Long,
    val iFollowThem: Boolean,
    val theyFollowMe: Boolean
)

data class Notification(
    val id: String,
    val type: String,
    val title: String,
    val body: String?,
    val targetType: String?,
    val targetId: String?,
    val readAt: String?,
    val createdAt: String
)

data class Inbox(val unreadCount: Long, val items: List<Notification>)
