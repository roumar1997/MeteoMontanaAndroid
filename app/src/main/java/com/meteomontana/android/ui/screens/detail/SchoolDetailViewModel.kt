package com.meteomontana.android.ui.screens.detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.storage.StorageUploadHelper
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.blocks.CreateBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.contributions.SubmitContributionUseCase
import com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.domain.usecase.notes.CreateNoteUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolByIdUseCase
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
        val notes: List<Note>,
        val isFavorite: Boolean,
        val blocks: List<BlockDto>,
        val isCurrentUserAdmin: Boolean = false
    ) : SchoolDetailUiState
}

@HiltViewModel
class SchoolDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSchoolById: GetSchoolByIdUseCase,
    private val getForecast: GetForecastUseCase,
    private val getNotes: GetNotesUseCase,
    private val createNote: CreateNoteUseCase,
    private val getMyFavorites: GetMyFavoritesUseCase,
    private val addFavorite: AddFavoriteUseCase,
    private val removeFavorite: RemoveFavoriteUseCase,
    private val getBlocks: GetBlocksUseCase,
    private val createBlock: CreateBlockUseCase,
    private val deleteBlockUseCase: DeleteBlockUseCase,
    private val submitContributionUseCase: SubmitContributionUseCase,
    private val getMyProfile: GetMyProfileUseCase,
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
                val school = getSchoolById(schoolId)
                val forecastResult = runCatching { getForecast(schoolId) }
                val notes  = runCatching { getNotes(schoolId) }.getOrDefault(emptyList())
                val isFav  = runCatching { getMyFavorites().any { it.id == schoolId } }.getOrDefault(false)
                val blocks = runCatching { getBlocks(schoolId) }.getOrDefault(emptyList())
                val isAdmin = runCatching { getMyProfile().isAdmin }.getOrDefault(false)
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
                if (cur.isFavorite) removeFavorite(schoolId)
                else addFavorite(schoolId)
                _uiState.value = cur.copy(isFavorite = !cur.isFavorite)
            } catch (_: Throwable) {}
        }
    }

    fun publishNote(text: String) {
        viewModelScope.launch {
            try {
                createNote(schoolId, text)
                val notes = getNotes(schoolId)
                val cur = _uiState.value
                if (cur is SchoolDetailUiState.Success) _uiState.value = cur.copy(notes = notes)
            } catch (_: Throwable) {}
        }
    }

    suspend fun submitContribution(req: ContributionRequest): Result<Unit> =
        runCatching { submitContributionUseCase(schoolId, req); Unit }

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
        submitContributionUseCase(schoolId, req)
        Unit
    }

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
            photoUrl = null,
            bloquesJson = bloques.toBloquesJson(),
            topoLinesJson = null
        )
        submitContributionUseCase(schoolId, req)
        Unit
    }

    fun addBlock(req: CreateBlockRequest) {
        viewModelScope.launch {
            try {
                createBlock(schoolId, req)
                val blocks = getBlocks(schoolId)
                val cur = _uiState.value
                if (cur is SchoolDetailUiState.Success) _uiState.value = cur.copy(blocks = blocks)
            } catch (_: Throwable) {}
        }
    }

    fun deleteBlock(blockId: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { deleteBlockUseCase(blockId) }.isSuccess
            if (ok) {
                val blocks = runCatching { getBlocks(schoolId) }.getOrDefault(emptyList())
                val cur = _uiState.value
                if (cur is SchoolDetailUiState.Success) _uiState.value = cur.copy(blocks = blocks)
            }
            onDone(ok)
        }
    }
}
