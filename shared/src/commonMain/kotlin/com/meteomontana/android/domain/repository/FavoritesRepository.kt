package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.FavoritesGrid
import com.meteomontana.android.domain.model.FavoriteSchool

interface FavoritesRepository {
    suspend fun getMyFavorites(): List<FavoriteSchool>
    suspend fun getFavoritesGrid(): FavoritesGrid
    suspend fun addFavorite(schoolId: String)
    suspend fun removeFavorite(schoolId: String)
}
