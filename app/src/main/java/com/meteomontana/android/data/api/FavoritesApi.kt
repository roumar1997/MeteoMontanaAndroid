package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.FavoriteSchoolDto
import com.meteomontana.android.data.api.dto.FavoritesGridDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FavoritesApi {

    @GET("me/favorites")
    suspend fun getMyFavorites(): List<FavoriteSchoolDto>

    @POST("me/favorites/{schoolId}")
    suspend fun addFavorite(@Path("schoolId") id: String)

    @DELETE("me/favorites/{schoolId}")
    suspend fun removeFavorite(@Path("schoolId") id: String)

    @GET("me/favorites/grid")
    suspend fun getFavoritesGrid(): FavoritesGridDto
}
