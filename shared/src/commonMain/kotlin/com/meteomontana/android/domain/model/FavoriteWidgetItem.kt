package com.meteomontana.android.domain.model

/**
 * Una escuela favorita lista para pintar en el widget de home. Modelo neutro
 * (KMP): lo produce GetFavoritesWidgetDataUseCase y lo consumen tanto el
 * widget Glance (Android) como el futuro widget WidgetKit (iOS).
 */
data class FavoriteWidgetItem(
    val id: String,
    val name: String,
    val score: Int,
    val dryRock: Boolean,
    val hours: List<Int>,
    val style: String?,
    val rock: String?,
    val distanceKm: Int?
)
