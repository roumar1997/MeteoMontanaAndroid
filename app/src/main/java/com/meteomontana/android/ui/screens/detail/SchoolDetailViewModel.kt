package com.meteomontana.android.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.CreateNoteRequest
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.NoteDto
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.repository.SchoolRepository
import com.meteomontana.android.util.toUserMessage
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
        val forecast: ForecastDto?,
        val forecastError: String?,
        val notes: List<NoteDto>,
        val isFavorite: Boolean,
        val blocks: List<BlockDto>
    ) : SchoolDetailUiState
}

@HiltViewModel
class SchoolDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SchoolRepository,
    private val api: SchoolApi
) : ViewModel() {

    private val schoolId: String = checkNotNull(savedStateHandle["schoolId"])

    private val _uiState = MutableStateFlow<SchoolDetailUiState>(SchoolDetailUiState.Loading)
    val uiState: StateFlow<SchoolDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = SchoolDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val school = repository.getSchoolById(schoolId)
                // El forecast puede fallar (Open-Meteo caído). El resto del detalle igual carga.
                val forecastResult = runCatching { api.getForecast(schoolId) }
                val notes  = runCatching { api.getNotesBySchool(schoolId) }.getOrDefault(emptyList())
                val isFav  = runCatching { api.getMyFavorites().any { it.id == schoolId } }.getOrDefault(false)
                val blocks = runCatching { api.getBlocks(schoolId) }.getOrDefault(emptyList())
                SchoolDetailUiState.Success(
                    school = school,
                    forecast = forecastResult.getOrNull(),
                    forecastError = forecastResult.exceptionOrNull()?.toUserMessage(),
                    notes = notes, isFavorite = isFav, blocks = blocks
                )
            } catch (t: Throwable) {
                SchoolDetailUiState.Error(t.toUserMessage())
            }
        }
    }

    fun toggleFavorite() {
        val cur = _uiState.value as? SchoolDetailUiState.Success ?: return
        viewModelScope.launch {
            try {
                if (cur.isFavorite) api.removeFavorite(schoolId)
                else api.addFavorite(schoolId)
                _uiState.value = cur.copy(isFavorite = !cur.isFavorite)
            } catch (_: Throwable) {}
        }
    }

    fun publishNote(text: String) {
        viewModelScope.launch {
            try {
                api.createNote(schoolId, CreateNoteRequest(text))
                val notes = api.getNotesBySchool(schoolId)
                val cur = _uiState.value
                if (cur is SchoolDetailUiState.Success) _uiState.value = cur.copy(notes = notes)
            } catch (_: Throwable) {}
        }
    }

    /** Envía una propuesta de mejora (parking, piedra, sector, corrección). */
    suspend fun submitContribution(req: ContributionRequest): Result<Unit> =
        runCatching { api.submitContribution(schoolId, req); Unit }

    fun addBlock(req: CreateBlockRequest) {
        viewModelScope.launch {
            try {
                api.createBlock(schoolId, req)
                val blocks = api.getBlocks(schoolId)
                val cur = _uiState.value
                if (cur is SchoolDetailUiState.Success) _uiState.value = cur.copy(blocks = blocks)
            } catch (_: Throwable) {}
        }
    }
}
