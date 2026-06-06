package com.meteomontana.android.domain.usecase.favorites

import com.meteomontana.android.data.api.FavoritesApi
import javax.inject.Inject

class RemoveFavoriteUseCase @Inject constructor(private val api: FavoritesApi) {
    suspend operator fun invoke(schoolId: String) = api.removeFavorite(schoolId)
}
