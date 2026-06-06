package com.meteomontana.android.domain.usecase.notifications

import com.meteomontana.android.domain.model.Inbox
import com.meteomontana.android.domain.repository.NotificationsRepository

class GetMyNotificationsUseCase(private val repository: NotificationsRepository) {
    suspend operator fun invoke(limit: Int = 50): Inbox = repository.getMyNotifications(limit)
}
