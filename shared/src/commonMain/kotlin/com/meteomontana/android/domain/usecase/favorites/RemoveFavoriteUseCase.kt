package com.meteomontana.android.domain.usecase.favorites

import com.meteomontana.android.domain.repository.FavoritesRepository

class RemoveFavoriteUseCase(private val repository: FavoritesRepository) {
    suspend operator fun invoke(schoolId: String) = repository.removeFavorite(schoolId)
}
