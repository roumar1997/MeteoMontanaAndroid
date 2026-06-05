package com.meteomontana.android.ui.screens.detail

import android.net.Uri
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
import com.meteomontana.android.data.storage.StorageUploadHelper
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
        val blocks: List<BlockDto>,
        val isCurrentUserAdmin: Boolean = false
    ) : SchoolDetailUiState
}

@HiltViewModel
class SchoolDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SchoolRepository,
    private val api: SchoolApi,
    private val storageHelper: StorageUploadHelper
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
                val isAdmin = runCatching { api.getMyProfile().isAdmin }.getOrDefault(false)
                SchoolDetailUiState.Success(
                    school = school,
                    forecast = forecastResult.getOrNull(),
                    forecastError = forecastResult.exceptionOrNull()?.toUserMessage(),
                    notes = notes, isFavorite = isFav, blocks = blocks,
                    isCurrentUserAdmin = isAdmin
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

    /** Envía una propuesta de mejora (parking, sector, corrección). */
    suspend fun submitContribution(req: ContributionRequest): Result<Unit> =
        runCatching { api.submitContribution(schoolId, req); Unit }

    /** Sube la foto a Firebase Storage (si hay) y envía la propuesta BOULDER al backend. */
    suspend fun submitBoulderContribution(
        lat: Double, lon: Double,
        name: String?,
        bloques: List<BoulderBloqueForm>,
        photoUri: Uri?
    ): Result<Unit> = runCatching {
        val photoUrl = if (photoUri != null) {
            storageHelper.uploadBoulderPhoto(photoUri, schoolId)
        } else null

        val req = ContributionRequest(
            type = "BOULDER",
            name = name?.takeIf { it.isNotBlank() },
            lat = lat,
            lon = lon,
            notes = null,
            description = null,
            proposedLat = null, proposedLon = null,
            correctionReason = null, targetBlockId = null,
            photoUrl = photoUrl,
            bloquesJson = bloques.toBloquesJson(),
            topoLinesJson = null
        )
        api.submitContribution(schoolId, req)
        Unit
    }

    /**
     * Envía una propuesta para AÑADIR VÍAS a una piedra existente.
     * No sube foto (se usa la del bloque) ni coordenadas (idem).
     * El backend al aprobar añade las líneas al bloque identificado por `targetBlockId`.
     */
    suspend fun submitAddLinesContribution(
        targetBlockId: String,
        targetLat: Double,
        targetLon: Double,
        bloques: List<BoulderBloqueForm>
    ): Result<Unit> = runCatching {
        val req = ContributionRequest(
            type = "BOULDER",
            name = null,
            lat = targetLat,
            lon = targetLon,
            notes = null,
            description = null,
            proposedLat = null, proposedLon = null,
            correctionReason = null,
            targetBlockId = targetBlockId,
            photoUrl = null,           // usa la foto del bloque existente
            bloquesJson = bloques.toBloquesJson(),
            topoLinesJson = null
        )
        api.submitContribution(schoolId, req)
        Unit
    }

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

    /** Borra un bloque (parking / piedra / zona). El backend valida que sea admin o creador. */
    fun deleteBlock(blockId: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { api.deleteBlock(blockId) }.isSuccess
            if (ok) {
                val blocks = runCatching { api.getBlocks(schoolId) }.getOrDefault(emptyList())
                val cur = _uiState.value
                if (cur is SchoolDetailUiState.Success) _uiState.value = cur.copy(blocks = blocks)
            }
            onDone(ok)
        }
    }
}
