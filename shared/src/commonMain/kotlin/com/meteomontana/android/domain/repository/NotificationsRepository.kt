package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.Inbox

interface NotificationsRepository {
    suspend fun getMyNotifications(limit: Int = 50): Inbox
    suspend fun markNotificationRead(id: String)
    suspend fun markAllNotificationsRead()
}
