package com.meteomontana.android.domain.model

data class FavoriteSchool(
    val id: String,
    val name: String,
    val region: String?,
    val rockType: String?,
    val isFavorite: Boolean
)

data class FavoritesGrid(val rows: List<FavoriteRow>, val days: List<String>)

data class FavoriteRow(val schoolId: String, val schoolName: String, val cells: List<DayCell>)

data class DayCell(val date: String, val avgScore: Int, val label: String)
