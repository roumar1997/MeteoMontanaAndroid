package com.meteomontana.android.ui.screens.day

import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.R
import com.meteomontana.android.util.AppText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.domain.model.HourForecast
import com.meteomontana.android.data.saved.SavedSchoolRepository
import com.meteomontana.android.domain.usecase.forecast.GetForecastByLocationUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DayDetailUiState {
    data object Loading : DayDetailUiState
    data class Loaded(
        val title: String,
        val day: DayForecast,
        val hoursOfDay: List<HourForecast>
    ) : DayDetailUiState
    data class Error(val message: String) : DayDetailUiState
}

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getForecast: GetForecastUseCase,
    private val getForecastByLocation: GetForecastByLocationUseCase,
    private val savedSchoolRepo: SavedSchoolRepository
) : ViewModel() {

    private val schoolId: String? = savedStateHandle["schoolId"]
    private val lat: Double? = savedStateHandle.get<String>("lat")?.toDoubleOrNull()
    private val lon: Double? = savedStateHandle.get<String>("lon")?.toDoubleOrNull()
    private val dayIndex: Int = savedStateHandle.get<String>("dayIndex")?.toIntOrNull() ?: 0

    private val _state = MutableStateFlow<DayDetailUiState>(DayDetailUiState.Loading)
    val state: StateFlow<DayDetailUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            // 1) Intenta backend. 2) Si falla y hay snapshot offline para esa escuela, úsalo.
            val forecastResult = runCatching {
                when {
                    !schoolId.isNullOrBlank() -> getForecast(schoolId)
                    lat != null && lon != null -> getForecastByLocation(lat, lon, null)
                    else -> error("Sin destino: falta schoolId o lat/lon")
                }
            }
            val forecast = forecastResult.getOrNull() ?: run {
                val snapshot = schoolId?.let {
                    runCatching { savedSchoolRepo.loadOffline(it) }.getOrNull()
                }
                snapshot?.forecast
            }
            _state.value = if (forecast == null) {
                DayDetailUiState.Error(
                    forecastResult.exceptionOrNull()?.toUserMessage()
                        ?: AppText.get(R.string.day_detail_no_data)
                )
            } else {
                val day = forecast.days.getOrNull(dayIndex)
                if (day == null) DayDetailUiState.Error(AppText.get(R.string.day_detail_out_of_range))
                else {
                    val isoDate = day.date.take(10)
                    val hours = forecast.hours.filter { it.time.take(10) == isoDate }
                    DayDetailUiState.Loaded(
                        title = formatTitle(day.date, dayIndex),
                        day = day,
                        hoursOfDay = hours
                    )
                }
            }
        }
    }

    private fun formatTitle(isoDate: String, idx: Int): String {
        if (idx == 0) return AppText.get(R.string.w_today)
        if (idx == 1) return AppText.get(R.string.w_tomorrow)
        // "2026-06-08" → "lunes, 8 de junio"
        return try {
            val (y, m, d) = isoDate.take(10).split("-").map { it.toInt() }
            val dayName = dayOfWeekName(y, m, d)
            AppText.get(R.string.day_title_format, dayName, d, monthName(m))
        } catch (_: Throwable) { isoDate }
    }

    private fun dayOfWeekName(y: Int, m: Int, d: Int): String {
        return CalendarLabels.daysLongMonFirst()[java.time.LocalDate.of(y, m, d).dayOfWeek.value - 1]
    }

    private fun monthName(m: Int) = CalendarLabels.monthsLong()[m - 1]
}
