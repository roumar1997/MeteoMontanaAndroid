package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorFavoritesApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.model.FavoritesGrid
import com.meteomontana.android.domain.repository.FavoritesRepository

class KtorFavoritesRepository(private val api: KtorFavoritesApi) : FavoritesRepository {

    override suspend fun getMyFavorites(): List<FavoriteSchool> =
        api.getMyFavorites().map { it.toDomain() }

    override suspend fun getFavoritesGrid(): FavoritesGrid =
        api.getFavoritesGrid().toDomain()

    override suspend fun addFavorite(schoolId: String) {
        api.addFavorite(schoolId)
    }

    override suspend fun removeFavorite(schoolId: String) {
        api.removeFavorite(schoolId)
    }
}
