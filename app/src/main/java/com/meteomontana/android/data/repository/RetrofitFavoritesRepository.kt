package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.FavoritesApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.FavoritesGrid
import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.repository.FavoritesRepository
import javax.inject.Inject

class RetrofitFavoritesRepository @Inject constructor(
    private val api: FavoritesApi
) : FavoritesRepository {
    override suspend fun getMyFavorites(): List<FavoriteSchool> =
        api.getMyFavorites().map { it.toDomain() }
    override suspend fun getFavoritesGrid(): FavoritesGrid = api.getFavoritesGrid().toDomain()
    override suspend fun addFavorite(schoolId: String) = api.addFavorite(schoolId)
    override suspend fun removeFavorite(schoolId: String) = api.removeFavorite(schoolId)
}
