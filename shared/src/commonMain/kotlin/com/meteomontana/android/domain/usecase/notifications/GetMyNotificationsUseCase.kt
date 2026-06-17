package com.meteomontana.android.domain.usecase.notifications

import com.meteomontana.android.domain.model.Inbox
import com.meteomontana.android.domain.repository.NotificationsRepository

class GetMyNotificationsUseCase(private val repository: NotificationsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(limit: Int = 50): Inbox = repository.getMyNotifications(limit)
}

class MarkNotificationReadUseCase(private val repository: NotificationsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String) = repository.markNotificationRead(id)
}

class MarkAllNotificationsReadUseCase(private val repository: NotificationsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke() = repository.markAllNotificationsRead()
}
