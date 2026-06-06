package com.meteomontana.android.domain.usecase.favorites

import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.repository.FavoritesRepository

class GetMyFavoritesUseCase(private val repository: FavoritesRepository) {
    suspend operator fun invoke(): List<FavoriteSchool> = repository.getMyFavorites()
}
