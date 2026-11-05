package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.R
import com.meteomontana.android.util.toUserMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.model.RangeScore
import com.meteomontana.android.domain.usecase.schools.GetRangeScoresUseCase
import com.meteomontana.android.domain.usecase.schools.SchoolFilterEngine
import com.meteomontana.android.domain.usecase.schools.GetSchoolCatalogUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.meteomontana.android.domain.util.Geo
import com.meteomontana.android.ui.screens.detail.BOULDER_GRADES

// Tipos de la pantalla Escuelas: filtros, orden, pestañas, grupos de Vías/Bloques y estado de la lista.

/** Un grupo de resultados "Vías/Bloques" por escuela (BLOCK_SEARCH_DESIGN.md §8.3). */
data class ExploreGroup(
    val schoolId: String,
    val schoolName: String,
    val hits: List<com.meteomontana.android.domain.model.LineSearchHit>
)

// Orden y etiquetas alineados con iOS (Todas/Bloque/Vía).
enum class StyleFilter(@androidx.annotation.StringRes val labelRes: Int, val apiValue: String?) {
    All(R.string.w_all, null),
    Boulder(R.string.w_boulder, "Bloque"),   // apiValue: valor del catálogo, NO se traduce
    Via(R.string.w_route, "Vía")
}

// iOS muestra "Mejor score" primero.
enum class SortBy(@androidx.annotation.StringRes val labelRes: Int) {
    Score(R.string.sort_best_score),
    Distance(R.string.sort_nearest)
}

val DISTANCE_OPTIONS = listOf<Double?>(null, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0, 350.0, 400.0, 450.0, 500.0)
// Alfabético, como iOS.
val ROCK_TYPES       = listOf("Granito", "Caliza", "Arenisca", "Basalto", "Conglomerado", "Pizarra")

// ── Pestaña "Escuelas" / "Vías/Bloques" (BLOCK_SEARCH_DESIGN.md §4.1b) ──
// Espejo EXACTO del diseño validado en iOS (builds 243→252) — ver esa
// sección para el porqué de cada decisión (el modo implícito sin pestaña
// se probó y se descartó).
enum class ExploreTab { Schools, Blocks }

/** "ORDENAR POR" del modo Vías/Bloques — NUNCA "Grado" (se probó y no
 *  aportaba nada). "Mejores condiciones" reordena en el CLIENTE: el score
 *  es meteorológico y el backend no lo conoce. */
enum class ExploreSortBy(@androidx.annotation.StringRes val labelRes: Int) {
    Distance(R.string.sort_nearest_explore),
    BestConditions(R.string.sort_best_conditions),
    /** Lo ordena el SERVIDOR: cuenta las personas que tienen cada vía hecha en su diario. */
    MostRepeated(R.string.sort_most_repeated)
}

/** Escalera REAL de grados (la misma que usa el editor de vías,
 *  `BOULDER_GRADES`) — nunca inventada. "PROY" no es un punto de la
 *  escalera, se excluye del rango. */
val EXPLORE_GRADE_LADDER: List<String> = BOULDER_GRADES.filter { it != "PROY" }

data class SchoolFilters(
    val style: StyleFilter = StyleFilter.All,
    val maxDistanceKm: Double? = 50.0,       // 50 km por defecto (como la PWA)
    val rockTypes: List<String> = emptyList(),
    val onlyFavorites: Boolean = false,
    val onlySavedOffline: Boolean = false,
    val sortBy: SortBy = SortBy.Score,       // ordenado por mejor score por defecto
    val query: String = ""
)

sealed interface SchoolListUiState {
    data object Loading : SchoolListUiState
    data class Success(val schools: List<School>) : SchoolListUiState
    data class Error(val message: String) : SchoolListUiState
}
