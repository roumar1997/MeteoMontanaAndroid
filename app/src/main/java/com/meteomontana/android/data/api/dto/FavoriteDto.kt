package com.meteomontana.android.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FavoriteSchoolDto(
    val id: String,
    val name: String,
    val region: String?,
    val rockType: String?,
    val isFavorite: Boolean
)

@JsonClass(generateAdapter = true)
data class FavoritesGridDto(
    val rows: List<FavoriteRowDto>,
    val days: List<String>
)

@JsonClass(generateAdapter = true)
data class FavoriteRowDto(
    val schoolId: String,
    val schoolName: String,
    val cells: List<DayCellDto>
)

@JsonClass(generateAdapter = true)
data class DayCellDto(
    val date: String,
    val avgScore: Int,
    val label: String
)
