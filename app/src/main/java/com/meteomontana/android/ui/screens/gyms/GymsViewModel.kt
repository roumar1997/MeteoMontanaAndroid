package com.meteomontana.android.ui.screens.gyms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymFilters
import com.meteomontana.android.domain.model.gym.GymListItem
import com.meteomontana.android.domain.model.gym.GymMode
import com.meteomontana.android.domain.model.gym.GymOrigin
import com.meteomontana.android.domain.usecase.gym.ClusterGymsUseCase
import com.meteomontana.android.domain.usecase.gym.FilterGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymFavoritesUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymsUseCase
import com.meteomontana.android.domain.usecase.gym.SetGymFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Capa que enseña el selector de Escuelas: la roca de siempre, los rocódromos o los muros gratis. */
enum class SchoolsLayer {
    ROCK, ROCODROMOS, FREE_WALLS;

    val gymMode: GymMode? get() = when (this) {
        ROCK -> null
        ROCODROMOS -> GymMode.ROCODROMOS
        FREE_WALLS -> GymMode.FREE_WALLS
    }
}

enum class GymsView { MAP, LIST }

data class GymsUiState(
    val loading: Boolean = false,
    /** Sin red y sin copia local: no hay nada que enseñar. */
    val loadFailed: Boolean = false,
    val all: List<Gym> = emptyList(),
    val items: List<GymListItem> = emptyList(),
    val chains: List<String> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val filters: GymFilters = GymFilters(),
    val view: GymsView = GymsView.MAP
)

/**
 * Estado de la capa de rocódromos de Escuelas. Solo habla con use cases de `shared`: el filtrado, el orden y
 * el "abierto ahora" son lógica común con iOS ([FilterGymsUseCase]).
 */
@HiltViewModel
class GymsViewModel @Inject constructor(
    private val getCachedGyms: GetCachedGymsUseCase,
    private val getGyms: GetGymsUseCase,
    private val filterGyms: FilterGymsUseCase,
    private val clusterGyms: ClusterGymsUseCase,
    private val getGymFavorites: GetGymFavoritesUseCase,
    private val setGymFavorite: SetGymFavoriteUseCase
) : ViewModel() {

    private val _layer = MutableStateFlow(SchoolsLayer.ROCK)
    val layer: StateFlow<SchoolsLayer> = _layer

    private val _all = MutableStateFlow<List<Gym>>(emptyList())
    private val _filters = MutableStateFlow(GymFilters())
    private val _origin = MutableStateFlow<GymOrigin?>(null)
    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    private val _view = MutableStateFlow(GymsView.MAP)
    private val _loading = MutableStateFlow(false)
    private val _failed = MutableStateFlow(false)
    private var started = false

    private val filtered = combine(_all, _filters, _origin, _favorites) { all, filters, origin, favs ->
        filterGyms(all, filters, origin, favs) to filterGyms.chains(all, filters.mode)
    }

    val state: StateFlow<GymsUiState> = combine(filtered, _all, _filters, _favorites, _view) { f, all, filters, favs, view ->
        GymsUiState(
            all = all, items = f.first, chains = f.second, favorites = favs, filters = filters, view = view
        )
    }.combine(_loading) { s, loading -> s.copy(loading = loading) }
        .combine(_failed) { s, failed -> s.copy(loadFailed = failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymsUiState())

    fun setLayer(layer: SchoolsLayer) {
        _layer.value = layer
        val mode = layer.gymMode ?: return
        // Al cambiar de capa se quitan los filtros que no valen en la otra (p. ej. "Gratis" en Muros gratis).
        _filters.update { it.copy(mode = mode, onlyFree = false, chain = null) }
        ensureLoaded()
    }

    /** Catálogo: primero la copia local (al instante) y luego la red. Se pide una sola vez por proceso. */
    fun ensureLoaded() {
        if (started) return
        started = true
        refresh()
    }

    fun refresh() {
        _all.value = getCachedGyms().also { if (it.isNotEmpty()) _failed.value = false }
        _loading.value = _all.value.isEmpty()
        viewModelScope.launch {
            runCatching { getGyms.execute() }
                .onSuccess { _all.value = it; _failed.value = false }
                .onFailure { _failed.value = _all.value.isEmpty() }
            _loading.value = false
            runCatching { getGymFavorites.execute() }.onSuccess { _favorites.value = it }
        }
    }

    /** Al volver de la ficha (donde se puede cambiar el favorito) se relee del servidor. */
    fun refreshFavorites() {
        viewModelScope.launch { runCatching { getGymFavorites.execute() }.onSuccess { _favorites.value = it } }
    }

    fun setOrigin(lat: Double?, lon: Double?) {
        _origin.value = if (lat != null && lon != null) GymOrigin(lat, lon) else null
    }

    fun setView(view: GymsView) { _view.value = view }

    /** Agrupación del mapa por zoom (rejilla común con iOS). */
    fun cluster(gyms: List<Gym>, zoom: Double) = clusterGyms(gyms, zoom)

    fun setQuery(q: String) = _filters.update { it.copy(query = q) }
    fun toggleFree() = _filters.update { it.copy(onlyFree = !it.onlyFree) }
    fun toggleBoulder() = _filters.update { it.copy(boulder = !it.boulder) }
    fun toggleRope() = _filters.update { it.copy(rope = !it.rope) }
    fun toggleOpenNow() = _filters.update { it.copy(openNow = !it.openNow) }
    fun toggleFavorites() = _filters.update { it.copy(onlyFavorites = !it.onlyFavorites) }
    fun setChain(chain: String?) = _filters.update { it.copy(chain = chain) }
    fun setMaxDistance(km: Double?) = _filters.update { it.copy(maxDistanceKm = km) }

    fun clearFilters() = _filters.update { GymFilters(mode = it.mode) }

    fun toggleFavorite(gymId: Long) {
        val now = gymId !in _favorites.value
        _favorites.update { if (now) it + gymId else it - gymId }
        viewModelScope.launch {
            runCatching { setGymFavorite.execute(gymId, now) }
                // Si el servidor lo rechaza (sin red, sin sesión) se deshace: lo que se ve es lo que hay guardado.
                .onFailure { _favorites.update { f -> if (now) f - gymId else f + gymId } }
        }
    }
}
