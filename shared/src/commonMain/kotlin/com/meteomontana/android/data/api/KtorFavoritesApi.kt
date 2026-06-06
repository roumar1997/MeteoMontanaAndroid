package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.FavoriteSchoolDto
import com.meteomontana.android.data.api.dto.FavoritesGridDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post

class KtorFavoritesApi(private val client: HttpClient) {

    suspend fun getMyFavorites(): List<FavoriteSchoolDto> = client.get("me/favorites").body()

    suspend fun getFavoritesGrid(): FavoritesGridDto = client.get("me/favorites/grid").body()

    suspend fun addFavorite(schoolId: String) { client.post("me/favorites/$schoolId") }

    suspend fun removeFavorite(schoolId: String) { client.delete("me/favorites/$schoolId") }
}
