package com.meteomontana.android.ui.screens.schools
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolCatalogUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.meteomontana.android.domain.util.Geo

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
    val onlySavedOffline: Boolean = false,
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
    private val getSchoolCatalog: GetSchoolCatalogUseCase,
    private val getTodayScores: GetTodayScoresUseCase,
    private val getMyFavorites: GetMyFavoritesUseCase,
    private val addFavorite: com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase,
    private val removeFavorite: com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase,
    private val getMyNotifications: GetMyNotificationsUseCase,
    private val locationProvider: LocationProvider,
    private val savedSchoolRepo: com.meteomontana.android.data.saved.SavedSchoolRepository,
    private val cachedSchoolsRepo: com.meteomontana.android.data.saved.CachedSchoolsRepository,
    private val etagStore: com.meteomontana.android.data.local.CatalogEtagStore
) : ViewModel() {

    // Catálogo completo en memoria (stale-while-revalidate): se pinta desde la
    // caché SQLDelight al instante y se refresca desde red en segundo plano.
    // Los filtros (estilo, roca, distancia, texto) se aplican en local — misma
    // semántica que el backend (equalsIgnoreCase + haversine), pero sin red.
    private var allSchools: List<School> = emptyList()

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

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    init {
        viewModelScope.launch {
            // 1) Pintar YA desde la caché local (si hay datos de una sesión anterior).
            allSchools = runCatching { cachedSchoolsRepo.load() }.getOrDefault(emptyList())
            if (allSchools.isNotEmpty()) load()

            // 2) Ubicación real (no bloquea: si no hay permiso, sigue con Madrid).
            locationProvider.current()?.let { loc ->
                userLat = loc.lat
                userLon = loc.lon
                _userLocation.value = loc
                if (allSchools.isNotEmpty()) load()  // re-filtra distancias con la posición real
            }
            _favoriteIds.value = runCatching { getMyFavorites().map { it.id }.toSet() }.getOrDefault(emptySet())

            // 3) Refrescar el catálogo desde red y actualizar caché + pantalla.
            refreshFromNetwork()
            refreshUnread()
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Pull-to-refresh y botón REINTENTAR: recarga catálogo, scores y favoritos. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _scores.value = emptyMap()   // fuerza scores frescos
            _favoriteIds.value = runCatching { getMyFavorites().map { it.id }.toSet() }.getOrDefault(_favoriteIds.value)
            refreshFromNetwork()
            refreshUnread()
            _isRefreshing.value = false
        }
    }

    /**
     * Baja el catálogo completo (sin filtros), lo cachea y re-emite la lista.
     * Manda el ETag guardado como If-None-Match: si el backend responde 304
     * (catálogo sin cambios), reusa la caché SQLDelight sin re-descargar.
     */
    private suspend fun refreshFromNetwork() {
        // Solo tiene sentido el condicional si hay caché que reusar en el 304.
        val etag = if (allSchools.isNotEmpty()) etagStore.get() else null
        runCatching {
            getSchoolCatalog(etag)
        }.onSuccess { catalog ->
            catalog.schools?.let { fresh ->
                allSchools = fresh
                runCatching { cachedSchoolsRepo.replaceAll(fresh) }
                etagStore.set(catalog.etag)
            }
            // Con 304 también re-emitimos: refresh() vació los scores y load()
            // dispara la recarga de los que falten.
            load()
        }.onFailure { t ->
            // Solo mostramos error si no teníamos nada que enseñar.
            if (allSchools.isEmpty()) _uiState.value = SchoolListUiState.Error(t.toUserMessage())
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
        val f = _filters.value
        viewModelScope.launch {
            // Modo offline: lee las escuelas guardadas en Room. Sin internet.
            if (f.onlySavedOffline) {
                _uiState.value = try {
                    val rows = savedSchoolRepo.observeSaved().first()
                    val list = rows.map {
                        School(
                            id = it.id, name = it.name, location = null,
                            region = it.region, style = null, rockType = it.rockType,
                            lat = it.lat, lon = it.lon, source = null
                        )
                    }
                    SchoolListUiState.Success(filterQuery(list, f.query))
                } catch (t: Throwable) {
                    SchoolListUiState.Error(t.toUserMessage())
                }
                return@launch
            }
            // Catálogo aún sin datos (primer arranque sin caché): spinner mientras
            // refreshFromNetwork() termina.
            if (allSchools.isEmpty()) {
                _uiState.value = SchoolListUiState.Loading
                return@launch
            }
            // Filtrado 100% local sobre el catálogo en memoria — cero red.
            var list = allSchools.asSequence()
                .filter { f.style.apiValue == null || f.style.apiValue.equals(it.style, ignoreCase = true) }
                .filter { f.rockTypes.isEmpty() || f.rockTypes.any { r -> r.equals(it.rockType, ignoreCase = true) } }
                .filter { f.maxDistanceKm == null || Geo.haversineKm(userLat, userLon, it.lat, it.lon) <= f.maxDistanceKm }
                .toList()
            list = filterQuery(list, f.query)
            if (f.onlyFavorites) list = list.filter { it.id in _favoriteIds.value }

            // Cargar scores en background para todas las visibles (lotes de 50)
            loadScoresFor(list.map { it.id }) {
                // tras cargar, re-emitimos el estado para que aplique el sort por score
                applySort(f)
            }

            _uiState.value = SchoolListUiState.Success(applySortInternal(list, f, _scores.value))
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
        SortBy.Distance -> list.sortedBy { Geo.haversineKm(userLat, userLon, it.lat, it.lon) }
        SortBy.Score    -> list.sortedByDescending { scores[it.id]?.todayScore ?: -1 }
    }

    private fun loadScoresFor(ids: List<String>, onDone: () -> Unit = {}) {
        // Solo pedimos los que aún no tenemos: teclear en el buscador re-llama
        // a load() y sin esto cada letra disparaba una llamada de red + un
        // re-sort que hacía saltar la lista.
        val missing = ids.filter { it !in _scores.value }
        if (missing.isEmpty()) { onDone(); return }
        viewModelScope.launch {
            // El backend acepta máximo 50 ids por petición → lotes encadenados.
            // Los scores van llegando y la lista se re-ordena progresivamente.
            missing.chunked(50).forEach { batch ->
                runCatching { getTodayScores(batch) }.onSuccess { results ->
                    _scores.update { it + results.associateBy { r -> r.id } }
                    onDone()
                }
            }
        }
    }

    /** Distancia entre el usuario y una escuela en km (Haversine). */
    fun distanceTo(schoolLat: Double, schoolLon: Double): Double =
        Geo.haversineKm(userLat, userLon, schoolLat, schoolLon)

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
    /** Marca/desmarca favorita desde la lista. Optimista: pinta ya, revierte si falla. */
    fun toggleFavorite(schoolId: String) {
        val wasFavorite = schoolId in _favoriteIds.value
        _favoriteIds.update { if (wasFavorite) it - schoolId else it + schoolId }
        viewModelScope.launch {
            runCatching {
                if (wasFavorite) removeFavorite(schoolId) else addFavorite(schoolId)
            }.onFailure {
                _favoriteIds.update { ids -> if (wasFavorite) ids + schoolId else ids - schoolId }
            }
            // Si el filtro "solo favoritas" está activo, la lista visible cambia.
            if (_filters.value.onlyFavorites) load()
        }
    }

    fun setOnlyFavorites(v: Boolean) {
        viewModelScope.launch {
            if (v) _favoriteIds.value = runCatching { getMyFavorites().map { it.id }.toSet() }.getOrDefault(_favoriteIds.value)
            _filters.update { it.copy(onlyFavorites = v) }
            load()
        }
    }
    fun setSort(s: SortBy)                  { _filters.update { it.copy(sortBy = s) }; load() }
    private var queryJob: kotlinx.coroutines.Job? = null
    fun setQuery(q: String) {
        // Debounce 200ms para evitar el efecto "terremoto" al teclear: la lista
        // ya no recarga en cada pulsación. Sí actualiza el filtro inmediato para
        // que la TextField siga responsiva.
        _filters.update { it.copy(query = q) }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            load()
        }
    }
    fun setOnlySavedOffline(v: Boolean)     { _filters.update { it.copy(onlySavedOffline = v) }; load() }

    // ── Selección para comparar (long-press en las cards, máx 3) ──
    private val _compareSelection = MutableStateFlow<Set<String>>(emptySet())
    val compareSelection: StateFlow<Set<String>> = _compareSelection.asStateFlow()

    fun toggleCompare(schoolId: String) {
        _compareSelection.update {
            when {
                schoolId in it -> it - schoolId
                it.size >= 3   -> it          // máximo 3
                else           -> it + schoolId
            }
        }
    }

    fun clearCompare() { _compareSelection.value = emptySet() }

    /** Botón "QUITAR FILTROS" del estado vacío: vuelve a los filtros por defecto sin límite de distancia. */
    fun clearFilters() {
        _filters.value = SchoolFilters(maxDistanceKm = null)
        load()
    }
}
