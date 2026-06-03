package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.JournalSessionDto
import com.meteomontana.android.data.api.dto.JournalStatsDto
import com.meteomontana.android.data.api.dto.NoteDto
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.api.dto.SchoolDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SchoolApi {

    @GET("schools")
    suspend fun getSchools(
        @Query("region")   region: String? = null,
        @Query("style")    style: String? = null,
        @Query("rockType") rockType: List<String>? = null,
        @Query("lat")      lat: Double? = null,
        @Query("lon")      lon: Double? = null,
        @Query("radioKm")  radioKm: Double? = null
    ): List<SchoolDto>

    @GET("schools/{id}")
    suspend fun getSchoolById(@Path("id") id: String): SchoolDto

    @GET("schools/{id}/notes")
    suspend fun getNotesBySchool(@Path("id") id: String): List<NoteDto>

    @POST("schools/{id}/notes")
    suspend fun createNote(@Path("id") id: String, @Body req: CreateNoteRequest): NoteDto

    @GET("schools/{id}/forecast")
    suspend fun getForecast(@Path("id") id: String): ForecastDto

    @GET("me")
    suspend fun getMyProfile(): PrivateProfileDto

    @POST("journal")
    suspend fun createJournalSession(@Body req: CreateJournalRequest): JournalSessionDto

    @GET("journal/me")
    suspend fun getMyJournal(): List<JournalSessionDto>

    @GET("journal/me/stats")
    suspend fun getMyJournalStats(): JournalStatsDto

    @DELETE("journal/{id}")
    suspend fun deleteJournalSession(@Path("id") id: String)
}
