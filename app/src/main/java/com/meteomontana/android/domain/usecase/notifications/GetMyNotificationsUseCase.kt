package com.meteomontana.android.domain.usecase.notifications

import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.InboxDto
import javax.inject.Inject

class GetMyNotificationsUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(limit: Int = 50): InboxDto = api.getMyNotifications(limit)
}
