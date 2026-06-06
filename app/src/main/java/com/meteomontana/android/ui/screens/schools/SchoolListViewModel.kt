package com.meteomontana.android.ui.screens.schools
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.location.LocationProvider
import com.meteomontana.android.data.location.UserLocation
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class StyleFilter(val label: String, val apiValue: String?) {
    All("Todos", null),
    Via("Vía", "Vía"),
    Boulder("Bloque", "Bloque")
}

enum class SortBy(val label: String) {
    Distance("Más cercanos"),
    Score("Mejor score")
}

val DISTANCE_OPTIONS = listOf<Double?>(null, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0, 350.0, 400.0, 450.0, 500.0)
val ROCK_TYPES       = listOf("Granito", "Caliza", "Arenisca", "Pizarra", "Basalto", "Conglomerado")

data class SchoolFilters(
    val style: StyleFilter = StyleFilter.All,
    val maxDistanceKm: Double? = 50.0,       // 50 km por defecto (como la PWA)
    val rockTypes: List<String> = emptyList(),
    val onlyFavorites: Boolean = false,
    val sortBy: SortBy = SortBy.Score,       // ordenado por mejor score por defecto
    val query: String = ""
)

sealed interface SchoolListUiState {
    data object Loading : SchoolListUiState
    data class Success(val schools: List<School>) : SchoolListUiState
    data class Error(val message: String) : SchoolListUiState
}

@HiltViewModel
class SchoolListViewModel @Inject constructor(
    private val getSchools: GetSchoolsUseCase,
    private val getTodayScores: GetTodayScoresUseCase,
    private val getMyFavorites: GetMyFavoritesUseCase,
    private val getMyNotifications: GetMyNotificationsUseCase,
    private val locationProvider: LocationProvider
) : ViewModel() {

    // Fallback Madrid si no hay permiso de ubicación; se sobreescribe al cargar.
    private var userLat: Double = 40.4168
    private var userLon: Double = -3.7038
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    private val _filters = MutableStateFlow(SchoolFilters())
    val filters: StateFlow<SchoolFilters> = _filters.asStateFlow()

    private val _uiState = MutableStateFlow<SchoolListUiState>(SchoolListUiState.Loading)
    val uiState: StateFlow<SchoolListUiState> = _uiState.asStateFlow()

    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount: StateFlow<Long> = _unreadCount.asStateFlow()

    private val _scores = MutableStateFlow<Map<String, com.meteomontana.android.domain.model.SchoolScore>>(emptyMap())
    val scores: StateFlow<Map<String, com.meteomontana.android.domain.model.SchoolScore>> = _scores.asStateFlow()

    private var favoriteIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            // 1) intentar obtener ubicación real (no bloquea: si no hay permiso, sigue con Madrid)
            locationProvider.current()?.let { loc ->
                userLat = loc.lat
                userLon = loc.lon
                _userLocation.value = loc
            }
            favoriteIds = runCatching { getMyFavorites().map { it.id }.toSet() }.getOrDefault(emptySet())
            load()
            refreshUnread()
        }
    }

    /** Llamado tras conceder permiso de ubicación. Refresca con la nueva posición. */
    fun onLocationGranted() {
        viewModelScope.launch {
            locationProvider.current()?.let { loc ->
                userLat = loc.lat
                userLon = loc.lon
                _userLocation.value = loc
                load()
            }
        }
    }

    fun refreshUnread() {
        viewModelScope.launch {
            runCatching { getMyNotifications(limit = 1) }.onSuccess {
                _unreadCount.value = it.unreadCount
            }
        }
    }

    fun load() {
        _uiState.value = SchoolListUiState.Loading
        val f = _filters.value
        viewModelScope.launch {
            _uiState.value = try {
                var list = getSchools(
                    style = f.style.apiValue,
                    rockType = f.rockTypes.takeIf { it.isNotEmpty() },
                    lat = if (f.maxDistanceKm != null) userLat else null,
                    lon = if (f.maxDistanceKm != null) userLon else null,
                    radioKm = f.maxDistanceKm
                )
                list = filterQuery(list, f.query)
                if (f.onlyFavorites) list = list.filter { it.id in favoriteIds }

                // Cargar scores en background para hasta 50 escuelas (límite del backend)
                loadScoresFor(list.take(50).map { it.id }) {
                    // tras cargar, re-emitimos el estado para que aplique el sort por score
                    applySort(f)
                }

                SchoolListUiState.Success(applySortInternal(list, f, _scores.value))
            } catch (t: Throwable) {
                SchoolListUiState.Error(t.toUserMessage())
            }
        }
    }

    /** Reordena el estado actual sin volver a llamar al backend. */
    private fun applySort(f: SchoolFilters) {
        val cur = _uiState.value as? SchoolListUiState.Success ?: return
        _uiState.value = SchoolListUiState.Success(applySortInternal(cur.schools, f, _scores.value))
    }

    private fun applySortInternal(
        list: List<School>,
        f: SchoolFilters,
        scores: Map<String, com.meteomontana.android.domain.model.SchoolScore>
    ): List<School> = when (f.sortBy) {
        SortBy.Distance -> list.sortedBy { haversineKm(userLat, userLon, it.lat, it.lon) }
        SortBy.Score    -> list.sortedByDescending { scores[it.id]?.todayScore ?: -1 }
    }

    private fun loadScoresFor(ids: List<String>, onDone: () -> Unit = {}) {
        if (ids.isEmpty()) { onDone(); return }
        viewModelScope.launch {
            runCatching { getTodayScores(ids) }.onSuccess { results ->
                _scores.value = results.associateBy { it.id }
                onDone()
            }
        }
    }

    /** Distancia entre el usuario y una escuela en km (Haversine). */
    fun distanceTo(schoolLat: Double, schoolLon: Double): Double =
        haversineKm(userLat, userLon, schoolLat, schoolLon)

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun filterQuery(list: List<School>, q: String): List<School> {
        val needle = q.trim().lowercase()
        if (needle.isBlank()) return list
        return list.filter {
            it.name.lowercase().contains(needle) ||
                    it.location?.lowercase()?.contains(needle) == true
        }
    }

    fun setStyle(style: StyleFilter)        { _filters.update { it.copy(style = style) }; load() }
    fun setDistance(km: Double?)            { _filters.update { it.copy(maxDistanceKm = km) }; load() }
    fun toggleRock(rock: String) {
        _filters.update {
            val newList = if (rock in it.rockTypes) it.rockTypes - rock else it.rockTypes + rock
            it.copy(rockTypes = newList)
        }
        load()
    }
    fun setOnlyFavorites(v: Boolean) {
        viewModelScope.launch {
            if (v) favoriteIds = runCatching { getMyFavorites().map { it.id }.toSet() }.getOrDefault(favoriteIds)
            _filters.update { it.copy(onlyFavorites = v) }
            load()
        }
    }
    fun setSort(s: SortBy)                  { _filters.update { it.copy(sortBy = s) }; load() }
    fun setQuery(q: String)                 { _filters.update { it.copy(query = q) }; load() }
}
