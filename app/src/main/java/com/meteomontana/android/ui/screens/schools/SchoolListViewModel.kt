package com.meteomontana.android.ui.screens.schools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val maxDistanceKm: Double? = null,
    val rockTypes: List<String> = emptyList(),
    val onlyFavorites: Boolean = false,
    val sortBy: SortBy = SortBy.Distance,
    val query: String = ""
)

sealed interface SchoolListUiState {
    data object Loading : SchoolListUiState
    data class Success(val schools: List<School>) : SchoolListUiState
    data class Error(val message: String) : SchoolListUiState
}

@HiltViewModel
class SchoolListViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val api: SchoolApi
) : ViewModel() {

    // Madrid stub. Mañana: usar FusedLocation.
    private val userLat = 40.4168
    private val userLon = -3.7038

    private val _filters = MutableStateFlow(SchoolFilters())
    val filters: StateFlow<SchoolFilters> = _filters.asStateFlow()

    private val _uiState = MutableStateFlow<SchoolListUiState>(SchoolListUiState.Loading)
    val uiState: StateFlow<SchoolListUiState> = _uiState.asStateFlow()

    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount: StateFlow<Long> = _unreadCount.asStateFlow()

    private val _scores = MutableStateFlow<Map<String, com.meteomontana.android.data.api.dto.SchoolScoreDto>>(emptyMap())
    val scores: StateFlow<Map<String, com.meteomontana.android.data.api.dto.SchoolScoreDto>> = _scores.asStateFlow()

    private var favoriteIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            favoriteIds = runCatching { api.getMyFavorites().map { it.id }.toSet() }.getOrDefault(emptySet())
            load()
            refreshUnread()
        }
    }

    fun refreshUnread() {
        viewModelScope.launch {
            runCatching { api.getMyNotifications(limit = 1) }.onSuccess {
                _unreadCount.value = it.unreadCount
            }
        }
    }

    fun load() {
        _uiState.value = SchoolListUiState.Loading
        val f = _filters.value
        viewModelScope.launch {
            _uiState.value = try {
                var list = schoolRepository.getSchools(
                    style = f.style.apiValue,
                    rockType = f.rockTypes.takeIf { it.isNotEmpty() },
                    lat = if (f.maxDistanceKm != null) userLat else null,
                    lon = if (f.maxDistanceKm != null) userLon else null,
                    radioKm = f.maxDistanceKm
                )
                list = filterQuery(list, f.query)
                if (f.onlyFavorites) list = list.filter { it.id in favoriteIds }
                // Cargar scores en background para los primeros 30
                loadScoresFor(list.take(30).map { it.id })
                SchoolListUiState.Success(list)
            } catch (t: Throwable) {
                SchoolListUiState.Error(t.message ?: "Error desconocido")
            }
        }
    }

    private fun loadScoresFor(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { api.getTodayScores(ids) }.onSuccess { results ->
                _scores.value = results.associateBy { it.id }
            }
        }
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
            if (v) favoriteIds = runCatching { api.getMyFavorites().map { it.id }.toSet() }.getOrDefault(favoriteIds)
            _filters.update { it.copy(onlyFavorites = v) }
            load()
        }
    }
    fun setSort(s: SortBy)                  { _filters.update { it.copy(sortBy = s) }; load() }
    fun setQuery(q: String)                 { _filters.update { it.copy(query = q) }; load() }
}
