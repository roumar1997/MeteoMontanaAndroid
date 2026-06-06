package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.FollowStatusDto
import com.meteomontana.android.data.api.dto.PublicProfileDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SocialApi {

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
}
