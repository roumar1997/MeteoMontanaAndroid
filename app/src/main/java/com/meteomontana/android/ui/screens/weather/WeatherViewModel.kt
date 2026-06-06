package com.meteomontana.android.ui.screens.weather
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.KtorFavoritesApi
import com.meteomontana.android.data.api.KtorForecastApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.location.LocationProvider
import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.model.FavoritesGrid
import com.meteomontana.android.domain.model.Forecast
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
        val selectedFavoriteId: String?,   // null = ubicación actual
        val grid: FavoritesGrid?
    ) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val forecastApi: KtorForecastApi,
    private val favoritesApi: KtorFavoritesApi,
    private val locationProvider: LocationProvider
) : ViewModel() {
    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

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
            // Cargamos favoritos y grid en paralelo a la ubicación.
            favorites = runCatching { favoritesApi.getMyFavorites().map { it.toDomain() } }.getOrDefault(emptyList())
            grid = runCatching { favoritesApi.getFavoritesGrid().toDomain() }.getOrNull()

            val loc = locationProvider.current() ?: return@launch run {
                _state.value = loadForecastByLatLon(40.4168, -3.7038)
            }
            _state.value = loadForecastByLatLon(loc.lat, loc.lon)
        }
    }

    fun selectFavorite(schoolId: String?) {
        viewModelScope.launch {
            _state.value = if (schoolId == null) {
                val loc = locationProvider.current()
                if (loc != null) loadForecastByLatLon(loc.lat, loc.lon)
                else loadForecastByLatLon(40.4168, -3.7038)
            } else {
                try {
                    val fc = forecastApi.getForecast(schoolId).toDomain()
                    WeatherUiState.Success(fc, favorites, schoolId, grid)
                } catch (t: Throwable) {
                    WeatherUiState.Error(t.toUserMessage())
                }
            }
        }
    }

    private suspend fun loadForecastByLatLon(lat: Double, lon: Double): WeatherUiState =
        try {
            WeatherUiState.Success(
                forecastApi.getForecastByLocation(lat, lon, null).toDomain(),
                favorites, null, grid
            )
        } catch (t: Throwable) {
            WeatherUiState.Error(t.toUserMessage())
        }
}
