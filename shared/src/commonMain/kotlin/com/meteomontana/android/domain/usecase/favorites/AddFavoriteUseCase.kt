package com.meteomontana.android.domain.usecase.favorites

import com.meteomontana.android.domain.repository.FavoritesRepository

class AddFavoriteUseCase(private val repository: FavoritesRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String) = repository.addFavorite(schoolId)
}
