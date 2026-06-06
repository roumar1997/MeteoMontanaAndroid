package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.api.dto.JournalSessionDto
import com.meteomontana.android.data.api.dto.JournalStatsDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface JournalApi {

    @POST("journal")
    suspend fun createJournalSession(@Body req: CreateJournalRequest): JournalSessionDto

    @GET("journal/me")
    suspend fun getMyJournal(): List<JournalSessionDto>

    @GET("journal/me/stats")
    suspend fun getMyJournalStats(): JournalStatsDto

    @DELETE("journal/{id}")
    suspend fun deleteJournalSession(@Path("id") id: String)
}
