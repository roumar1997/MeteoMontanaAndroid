package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorNotificationApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Inbox
import com.meteomontana.android.domain.repository.NotificationsRepository

class KtorNotificationsRepository(private val api: KtorNotificationApi) : NotificationsRepository {

    override suspend fun getMyNotifications(limit: Int): Inbox =
        api.getMyNotifications(limit).toDomain()

    override suspend fun markNotificationRead(id: String) = api.markNotificationRead(id)

    override suspend fun markAllNotificationsRead() = api.markAllNotificationsRead()

    override suspend fun deleteNotification(id: String) = api.deleteNotification(id)

    override suspend fun deleteAllNotifications() = api.deleteAllNotifications()
}
