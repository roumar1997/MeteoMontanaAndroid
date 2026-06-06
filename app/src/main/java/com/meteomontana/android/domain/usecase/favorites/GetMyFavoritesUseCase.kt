package com.meteomontana.android.domain.usecase.favorites

import com.meteomontana.android.data.api.FavoritesApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.FavoriteSchool
import javax.inject.Inject

class GetMyFavoritesUseCase @Inject constructor(private val api: FavoritesApi) {
    suspend operator fun invoke(): List<FavoriteSchool> =
        api.getMyFavorites().map { it.toDomain() }
}
