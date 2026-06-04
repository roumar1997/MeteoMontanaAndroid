package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.AdminLogDto
import com.meteomontana.android.data.api.dto.AdminPushRequest
import com.meteomontana.android.data.api.dto.AdminPushResponse
import com.meteomontana.android.data.api.dto.AdminStatsDto
import com.meteomontana.android.data.api.dto.RejectReason
import com.meteomontana.android.data.api.dto.SubmissionDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminApi {

    @GET("admin/stats")
    suspend fun stats(): AdminStatsDto

    @GET("admin/submissions")
    suspend fun pendingSubmissions(): List<SubmissionDto>

    @POST("admin/submissions/{id}/approve")
    suspend fun approve(@Path("id") id: String): SubmissionDto

    @POST("admin/submissions/{id}/reject")
    suspend fun reject(@Path("id") id: String, @Body req: RejectReason): SubmissionDto

    @GET("admin/logs")
    suspend fun logs(@Query("limit") limit: Int = 100): List<AdminLogDto>

    @POST("admin/push")
    suspend fun sendPush(@Body req: AdminPushRequest): AdminPushResponse
}
