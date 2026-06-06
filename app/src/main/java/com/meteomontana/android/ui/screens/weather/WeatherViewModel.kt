package com.meteomontana.android.ui.screens.weather
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.location.LocationProvider
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
            favorites = runCatching { getMyFavorites() }.getOrDefault(emptyList())

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
}
