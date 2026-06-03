package com.meteomontana.android.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SchoolDetailUiState {
    data object Loading : SchoolDetailUiState
    data class Error(val message: String) : SchoolDetailUiState
    data class Success(
        val school: School,
        val forecast: ForecastDto
    ) : SchoolDetailUiState
}

@HiltViewModel
class SchoolDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SchoolRepository,
    private val api: SchoolApi
) : ViewModel() {

    private val schoolId: String = checkNotNull(savedStateHandle["schoolId"]) {
        "schoolId is required"
    }

    private val _uiState = MutableStateFlow<SchoolDetailUiState>(SchoolDetailUiState.Loading)
    val uiState: StateFlow<SchoolDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = SchoolDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val school = repository.getSchoolById(schoolId)
                val forecast = api.getForecast(schoolId)
                SchoolDetailUiState.Success(school, forecast)
            } catch (t: Throwable) {
                SchoolDetailUiState.Error(t.message ?: "Error desconocido")
            }
        }
    }
}
