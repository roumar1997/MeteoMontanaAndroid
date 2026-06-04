package com.meteomontana.android.ui.screens.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
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
    data class Success(val forecast: ForecastDto) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val api: SchoolApi,
    private val locationProvider: LocationProvider
) : ViewModel() {
    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    init { tryLoad() }

    fun tryLoad() {
        _state.value = WeatherUiState.Loading
        viewModelScope.launch {
            if (!locationProvider.hasPermission()) {
                _state.value = WeatherUiState.NeedPermission
                return@launch
            }
            val loc = locationProvider.current()
            if (loc == null) {
                // Sin GPS reciente; usamos Madrid como fallback
                _state.value = loadForecast(40.4168, -3.7038)
            } else {
                _state.value = loadForecast(loc.lat, loc.lon)
            }
        }
    }

    private suspend fun loadForecast(lat: Double, lon: Double): WeatherUiState =
        try {
            WeatherUiState.Success(api.getForecastByLocation(lat, lon, null))
        } catch (t: Throwable) {
            WeatherUiState.Error(t.message ?: "Error")
        }
}
