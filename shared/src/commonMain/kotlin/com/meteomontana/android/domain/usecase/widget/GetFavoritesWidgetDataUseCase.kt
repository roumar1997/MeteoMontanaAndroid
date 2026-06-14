package com.meteomontana.android.domain.usecase.widget

import com.meteomontana.android.data.saved.CachedSchoolsRepository
import com.meteomontana.android.domain.model.FavoriteWidgetItem
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import com.meteomontana.android.domain.util.Geo
import kotlin.math.roundToInt

/**
 * Ensambla los datos del widget "Favoritas hoy": las favoritas del usuario
 * con su score de hoy, ordenadas de mejor a peor, enriquecidas con
 * estilo/roca del catálogo cacheado y la distancia desde la ubicación actual.
 *
 * Toda la lógica de negocio del widget vive aquí (KMP, commonMain): el widget
 * Glance de Android y el futuro WidgetKit de iOS solo renderizan el resultado.
 */
class GetFavoritesWidgetDataUseCase(
    private val getMyFavorites: GetMyFavoritesUseCase,
    private val getTodayScores: GetTodayScoresUseCase,
    private val cachedSchools: CachedSchoolsRepository,
    private val locationProvider: LocationProvider
) {
    companion object {
        const val MAX_SCHOOLS = 8
        const val HEATMAP_CELLS = 12
    }

    /**
     * Lista de favoritas lista para el widget. Vacía si el usuario no tiene
     * favoritas. Lanza si falla la red/sesión (el widget cae a su caché).
     */
    suspend operator fun invoke(): List<FavoriteWidgetItem> {
        val favorites = getMyFavorites().take(MAX_SCHOOLS)
        if (favorites.isEmpty()) return emptyList()

        val scores = getTodayScores(favorites.map { it.id }).associateBy { it.id }
        // Contexto local, sin red. Si falta, esa parte se omite.
        val catalog = runCatching { cachedSchools.load() }
            .getOrNull().orEmpty().associateBy { it.id }
        val loc = runCatching { locationProvider.current() }.getOrNull()

        return favorites.map { fav ->
            val s = scores[fav.id]
            val cat = catalog[fav.id]
            FavoriteWidgetItem(
                id = fav.id,
                name = fav.name,
                score = s?.todayScore ?: -1,
                dryRock = s?.dryRock ?: true,
                hours = s?.hourlyScores?.take(HEATMAP_CELLS).orEmpty(),
                style = cat?.style,
                rock = cat?.rockType ?: fav.rockType,
                distanceKm = if (loc != null && cat != null)
                    Geo.haversineKm(loc.lat, loc.lon, cat.lat, cat.lon).roundToInt()
                else null
            )
        }.sortedByDescending { it.score }
    }
}
