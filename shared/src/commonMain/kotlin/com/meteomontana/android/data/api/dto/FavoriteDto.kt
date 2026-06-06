package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteSchoolDto(
    val id: String,
    val name: String,
    val region: String? = null,
    val rockType: String? = null,
    val isFavorite: Boolean
)

@Serializable
data class FavoritesGridDto(
    val rows: List<FavoriteRowDto>,
    val days: List<String>
)

@Serializable
data class FavoriteRowDto(
    val schoolId: String,
    val schoolName: String,
    val cells: List<DayCellDto>
)

@Serializable
data class DayCellDto(
    val date: String,
    val avgScore: Int,
    val label: String
)
