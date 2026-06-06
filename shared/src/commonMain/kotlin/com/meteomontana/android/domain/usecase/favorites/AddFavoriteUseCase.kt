package com.meteomontana.android.domain.usecase.favorites

import com.meteomontana.android.domain.repository.FavoritesRepository

class AddFavoriteUseCase(private val repository: FavoritesRepository) {
    suspend operator fun invoke(schoolId: String) = repository.addFavorite(schoolId)
}
