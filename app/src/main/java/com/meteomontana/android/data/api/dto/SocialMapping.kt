package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.Inbox
import com.meteomontana.android.domain.model.Notification
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.PublicProfile

fun PrivateProfileDto.toDomain() = PrivateProfile(
    uid, email, username, displayName, photoUrl, bio, topGrade,
    isPublic, isAdmin, isPremium
)

fun PublicProfileDto.toDomain() = PublicProfile(
    uid, username, displayName, photoUrl, bio, topGrade
)

fun FollowStatusDto.toDomain() = FollowStatus(
    followers, following, iFollowThem, theyFollowMe
)

fun NotificationDto.toDomain() = Notification(
    id, type, title, body, targetType, targetId, readAt, createdAt
)

fun InboxDto.toDomain() = Inbox(unreadCount, items.map { it.toDomain() })
