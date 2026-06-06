package com.meteomontana.android.domain.usecase.notifications

import com.meteomontana.android.data.api.NotificationApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Inbox
import javax.inject.Inject

class GetMyNotificationsUseCase @Inject constructor(private val api: NotificationApi) {
    suspend operator fun invoke(limit: Int = 50): Inbox = api.getMyNotifications(limit).toDomain()
}
