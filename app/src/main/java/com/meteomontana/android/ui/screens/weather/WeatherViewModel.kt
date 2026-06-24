package com.meteomontana.android.ui.screens.weather
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.model.FavoritesGrid
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastByLocationUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data object NeedPermission : WeatherUiState
    data class Success(
        val forecast: Forecast,
        val favorites: List<FavoriteSchool>,
        val selectedFavoriteId: String?,
        val grid: FavoritesGrid?
    ) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val getForecast: GetForecastUseCase,
    private val getForecastByLocation: GetForecastByLocationUseCase,
    private val getMyFavorites: GetMyFavoritesUseCase,
    private val getFavoritesGrid: com.meteomontana.android.domain.usecase.favorites.GetFavoritesGridUseCase,
    private val searchPlaces: com.meteomontana.android.domain.usecase.forecast.SearchPlacesUseCase,
    private val locationProvider: LocationProvider
) : ViewModel() {
    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    // Buscador del tiempo por pueblos.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _placeResults = MutableStateFlow<List<com.meteomontana.android.domain.model.Place>>(emptyList())
    val placeResults: StateFlow<List<com.meteomontana.android.domain.model.Place>> = _placeResults.asStateFlow()
    private val _selectedPlaceName = MutableStateFlow<String?>(null)
    val selectedPlaceName: StateFlow<String?> = _selectedPlaceName.asStateFlow()
    private var searchJob: kotlinx.coroutines.Job? = null

    private var favorites: List<FavoriteSchool> = emptyList()
    private var grid: FavoritesGrid? = null

    init { tryLoad() }

    fun tryLoad() {
        _state.value = WeatherUiState.Loading
        viewModelScope.launch {
            if (!locationProvider.hasPermission()) {
                _state.value = WeatherUiState.NeedPermission
                return@launch
            }
            favorites = runCatching { getMyFavorites() }.getOrDefault(emptyList())
            grid = runCatching { getFavoritesGrid() }.getOrNull()

            val loc = locationProvider.current() ?: return@launch run {
                _state.value = loadForecastByLatLon(40.4168, -3.7038)
            }
            _state.value = loadForecastByLatLon(loc.lat, loc.lon)
        }
    }

    fun selectFavorite(schoolId: String?) {
        _selectedPlaceName.value = null
        viewModelScope.launch {
            _state.value = if (schoolId == null) {
                val loc = locationProvider.current()
                if (loc != null) loadForecastByLatLon(loc.lat, loc.lon)
                else loadForecastByLatLon(40.4168, -3.7038)
            } else {
                try {
                    WeatherUiState.Success(getForecast(schoolId), favorites, schoolId, grid)
                } catch (t: Throwable) {
                    WeatherUiState.Error(t.toUserMessage())
                }
            }
        }
    }

    private suspend fun loadForecastByLatLon(lat: Double, lon: Double): WeatherUiState =
        try {
            WeatherUiState.Success(getForecastByLocation(lat, lon, null), favorites, null, grid)
        } catch (t: Throwable) {
            WeatherUiState.Error(t.toUserMessage())
        }

    /** Escribe en el buscador de pueblos (con debounce). */
    fun onQueryChange(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { _placeResults.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            _placeResults.value = runCatching { searchPlaces(q) }.getOrDefault(emptyList())
        }
    }

    /** Elige un pueblo del buscador → muestra su tiempo. */
    fun selectPlace(place: com.meteomontana.android.domain.model.Place) {
        _query.value = ""
        _placeResults.value = emptyList()
        _selectedPlaceName.value = place.name.substringBefore(",").trim()
        _state.value = WeatherUiState.Loading
        viewModelScope.launch { _state.value = loadForecastByLatLon(place.lat, place.lon) }
    }

    /** Vuelve a mi ubicación (limpia el pueblo buscado). */
    fun clearSearchedPlace() {
        _selectedPlaceName.value = null
        _query.value = ""
        _placeResults.value = emptyList()
        tryLoad()
    }
}
