package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.NotificationApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Inbox
import com.meteomontana.android.domain.repository.NotificationsRepository
import javax.inject.Inject

class RetrofitNotificationsRepository @Inject constructor(
    private val api: NotificationApi
) : NotificationsRepository {
    override suspend fun getMyNotifications(limit: Int): Inbox = api.getMyNotifications(limit).toDomain()
}
