package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.JournalSessionDto
import com.meteomontana.android.data.api.dto.JournalStatsDto
import com.meteomontana.android.data.api.dto.NoteDto
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.api.dto.SchoolDto
import com.meteomontana.android.data.api.dto.SubmissionDto
import com.meteomontana.android.data.api.dto.SubmitSchoolRequest
import com.meteomontana.android.data.api.dto.FavoriteSchoolDto
import com.meteomontana.android.data.api.dto.FavoritesGridDto
import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.api.dto.FollowStatusDto
import com.meteomontana.android.data.api.dto.InboxDto
import com.meteomontana.android.data.api.dto.PublicProfileDto
import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.SchoolScoreDto
import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Part
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

    @GET("schools/search")
    suspend fun searchSchools(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): List<SchoolDto>

    @GET("schools/{id}")
    suspend fun getSchoolById(@Path("id") id: String): SchoolDto

    @GET("schools/{id}/notes")
    suspend fun getNotesBySchool(@Path("id") id: String): List<NoteDto>

    @POST("schools/{id}/notes")
    suspend fun createNote(@Path("id") id: String, @Body req: CreateNoteRequest): NoteDto

    @GET("schools/{id}/forecast")
    suspend fun getForecast(@Path("id") id: String): ForecastDto

    @GET("forecast/by-location")
    suspend fun getForecastByLocation(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("rockType") rockType: String? = null
    ): ForecastDto

    @GET("me")
    suspend fun getMyProfile(): PrivateProfileDto

    @PUT("me")
    suspend fun updateMyProfile(@Body req: UpdateProfileRequest): PrivateProfileDto

    @Multipart
    @POST("me/photo")
    suspend fun uploadMyPhoto(@Part file: MultipartBody.Part): PrivateProfileDto

    @PUT("me/fcm-token")
    suspend fun updateFcmToken(@Body req: FcmTokenRequest)

    @GET("me/favorites")
    suspend fun getMyFavorites(): List<FavoriteSchoolDto>

    @POST("me/favorites/{schoolId}")
    suspend fun addFavorite(@Path("schoolId") id: String)

    @DELETE("me/favorites/{schoolId}")
    suspend fun removeFavorite(@Path("schoolId") id: String)

    @GET("me/favorites/grid")
    suspend fun getFavoritesGrid(): FavoritesGridDto

    @POST("journal")
    suspend fun createJournalSession(@Body req: CreateJournalRequest): JournalSessionDto

    @GET("journal/me")
    suspend fun getMyJournal(): List<JournalSessionDto>

    @GET("journal/me/stats")
    suspend fun getMyJournalStats(): JournalStatsDto

    @DELETE("journal/{id}")
    suspend fun deleteJournalSession(@Path("id") id: String)

    @POST("submissions")
    suspend fun submitSchool(@Body req: SubmitSchoolRequest): SubmissionDto

    @GET("submissions/me")
    suspend fun getMySubmissions(): List<SubmissionDto>

    // ===== Contributions (mejoras de escuelas existentes) =====

    @POST("schools/{schoolId}/contributions")
    suspend fun submitContribution(
        @Path("schoolId") schoolId: String,
        @Body req: ContributionRequest
    ): ContributionDto

    @GET("contributions/me")
    suspend fun getMyContributions(): List<ContributionDto>

    // ===== Social =====
    @GET("users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): List<PublicProfileDto>

    @GET("users/{uid}")
    suspend fun getUserProfile(@Path("uid") uid: String): PublicProfileDto

    @POST("users/{uid}/follow")
    suspend fun follow(@Path("uid") uid: String)

    @DELETE("users/{uid}/follow")
    suspend fun unfollow(@Path("uid") uid: String)

    @GET("users/{uid}/follow-status")
    suspend fun getFollowStatus(@Path("uid") uid: String): FollowStatusDto

    @GET("users/{uid}/followers")
    suspend fun getFollowers(@Path("uid") uid: String): List<PublicProfileDto>

    @GET("users/{uid}/following")
    suspend fun getFollowing(@Path("uid") uid: String): List<PublicProfileDto>

    // ===== Notifications inbox =====
    @GET("me/notifications")
    suspend fun getMyNotifications(@Query("limit") limit: Int = 50): InboxDto

    @POST("me/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String)

    @POST("me/notifications/read-all")
    suspend fun markAllNotificationsRead()

    // ===== Blocks =====
    @GET("schools/{id}/blocks")
    suspend fun getBlocks(@Path("id") id: String): List<BlockDto>

    @GET("blocks/{id}")
    suspend fun getBlock(@Path("id") id: String): BlockDto

    @POST("schools/{id}/blocks")
    suspend fun createBlock(@Path("id") id: String, @Body req: CreateBlockRequest): BlockDto

    @PUT("blocks/{id}")
    suspend fun updateBlock(@Path("id") id: String, @Body req: CreateBlockRequest): BlockDto

    @DELETE("blocks/{id}")
    suspend fun deleteBlock(@Path("id") id: String)

    // ===== Today scores batch =====
    @GET("forecast/today-scores")
    suspend fun getTodayScores(@Query("ids") ids: List<String>): List<SchoolScoreDto>
}

