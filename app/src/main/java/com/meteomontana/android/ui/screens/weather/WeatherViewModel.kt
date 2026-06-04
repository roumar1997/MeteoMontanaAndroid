package com.meteomontana.android.ui.screens.weather
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.FavoriteSchoolDto
import com.meteomontana.android.data.api.dto.FavoritesGridDto
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.location.LocationProvider
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
        val forecast: ForecastDto,
        val favorites: List<FavoriteSchoolDto>,
        val selectedFavoriteId: String?,   // null = ubicación actual
        val grid: FavoritesGridDto?
    ) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val api: SchoolApi,
    private val locationProvider: LocationProvider
) : ViewModel() {
    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private var favorites: List<FavoriteSchoolDto> = emptyList()
    private var grid: FavoritesGridDto? = null

    init { tryLoad() }

    fun tryLoad() {
        _state.value = WeatherUiState.Loading
        viewModelScope.launch {
            if (!locationProvider.hasPermission()) {
                _state.value = WeatherUiState.NeedPermission
                return@launch
            }
            // Cargamos favoritos y grid en paralelo a la ubicación.
            favorites = runCatching { api.getMyFavorites() }.getOrDefault(emptyList())
            grid = runCatching { api.getFavoritesGrid() }.getOrNull()

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
                    val fc = api.getForecast(schoolId)
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
                api.getForecastByLocation(lat, lon, null),
                favorites, null, grid
            )
        } catch (t: Throwable) {
            WeatherUiState.Error(t.toUserMessage())
        }
}
