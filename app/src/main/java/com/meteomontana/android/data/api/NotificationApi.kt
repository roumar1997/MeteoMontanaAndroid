package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.InboxDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {

    @GET("me/notifications")
    suspend fun getMyNotifications(@Query("limit") limit: Int = 50): InboxDto

    @POST("me/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String)

    @POST("me/notifications/read-all")
    suspend fun markAllNotificationsRead()
}
