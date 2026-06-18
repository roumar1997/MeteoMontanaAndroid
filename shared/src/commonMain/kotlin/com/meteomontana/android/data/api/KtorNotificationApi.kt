package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.InboxDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

class KtorNotificationApi(private val client: HttpClient) {

    suspend fun getMyNotifications(limit: Int = 50): InboxDto =
        client.get("me/notifications") { parameter("limit", limit) }.body()

    suspend fun markNotificationRead(id: String) { client.post("me/notifications/$id/read") }

    suspend fun markAllNotificationsRead() { client.post("me/notifications/read-all") }

    suspend fun deleteNotification(id: String) { client.delete("me/notifications/$id") }

    suspend fun deleteAllNotifications() { client.delete("me/notifications") }
}
