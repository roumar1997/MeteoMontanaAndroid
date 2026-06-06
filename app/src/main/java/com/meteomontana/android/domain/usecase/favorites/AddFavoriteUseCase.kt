package com.meteomontana.android.domain.usecase.favorites

import com.meteomontana.android.data.api.SchoolApi
import javax.inject.Inject

class AddFavoriteUseCase @Inject constructor(private val api: SchoolApi) {
    suspend operator fun invoke(schoolId: String) = api.addFavorite(schoolId)
}
