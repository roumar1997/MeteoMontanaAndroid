package com.meteomontana.android.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.stats.MonthlyStats
import com.meteomontana.android.data.stats.MonthlyStatsRepository
import com.meteomontana.android.data.saved.SavedSchoolRepository
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.Forecast
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
        val forecast: Forecast?,
        val forecastError: String?,
        val notes: List<Note>,
        val isFavorite: Boolean,
        val blocks: List<Block>,
        val isCurrentUserAdmin: Boolean = false,
        val monthlyStats: MonthlyStats? = null,
        val monthlyLoading: Boolean = false,
        val isSavedOffline: Boolean = false,
        /** Si !=null, los datos provienen del snapshot offline guardado en esa fecha (epoch ms). */
        val offlineSnapshotAt: Long? = null
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
    private val photoUploader: PhotoUploader,
    private val fileReader: FileReader,
    private val monthlyStatsRepo: MonthlyStatsRepository,
    private val savedSchoolRepo: SavedSchoolRepository,
    private val offlineTiles: com.meteomontana.android.data.map.OfflineTileManager,
    private val ktorAdminApi: com.meteomontana.android.data.api.KtorAdminApi,
    private val updateBlockUseCase: com.meteomontana.android.domain.usecase.blocks.UpdateBlockUseCase,
    private val outboxRepo: com.meteomontana.android.data.outbox.OutboxRepository,
    private val networkMonitor: com.meteomontana.android.domain.port.NetworkMonitor
) : ViewModel() {

    private val schoolId: String = checkNotNull(savedStateHandle["schoolId"])

    private val _uiState = MutableStateFlow<SchoolDetailUiState>(SchoolDetailUiState.Loading)
    val uiState: StateFlow<SchoolDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = SchoolDetailUiState.Loading
        viewModelScope.launch {
            // Si el backend falla pero la escuela está guardada offline → modo offline.
            val schoolFromNet = runCatching { getSchoolById(schoolId) }
            if (schoolFromNet.isFailure) {
                val snapshot = runCatching { savedSchoolRepo.loadOffline(schoolId) }.getOrNull()
                if (snapshot != null) {
                    _uiState.value = SchoolDetailUiState.Success(
                        school = School(
                            id = snapshot.school.id, name = snapshot.school.name,
                            location = null, region = snapshot.school.region,
                            style = null, rockType = snapshot.school.rockType,
                            lat = snapshot.school.lat, lon = snapshot.school.lon,
                            source = null
                        ),
                        forecast = snapshot.forecast,
                        forecastError = if (snapshot.forecast == null)
                            "Sin conexión y sin snapshot — solo mapa offline" else null,
                        notes = emptyList(),
                        isFavorite = false,
                        blocks = snapshot.blocks.map { savedSchoolRepo.toBlock(it, snapshot.lines) },
                        isCurrentUserAdmin = false,
                        isSavedOffline = true,
                        monthlyLoading = false,
                        offlineSnapshotAt = snapshot.forecastFetchedAt
                    )
                    return@launch
                }
            }
            _uiState.value = try {
                val school = schoolFromNet.getOrThrow()
                val forecastResult = runCatching { getForecast(schoolId) }
                val notes  = runCatching { getNotes(schoolId) }.getOrDefault(emptyList())
                val isFav  = runCatching { getMyFavorites().any { it.id == schoolId } }.getOrDefault(false)
                val blocks = runCatching { getBlocks(schoolId) }.getOrDefault(emptyList())
                val isAdmin = runCatching { getMyProfile().isAdmin }.getOrDefault(false)
                val isSaved = runCatching { savedSchoolRepo.loadOffline(schoolId) != null }.getOrDefault(false)
                val success = SchoolDetailUiState.Success(
                    school = school,
                    forecast = forecastResult.getOrNull(),
                    forecastError = forecastResult.exceptionOrNull()?.toUserMessage(),
                    notes = notes, isFavorite = isFav, blocks = blocks,
                    isCurrentUserAdmin = isAdmin,
                    isSavedOffline = isSaved,
                    monthlyLoading = true
                )
                viewModelScope.launch { loadMonthlyStats(school) }
                success
            } catch (t: Throwable) {
                SchoolDetailUiState.Error(t.toUserMessage())
            }
        }
    }

    private suspend fun loadMonthlyStats(school: School) {
        val stats = runCatching {
            monthlyStatsRepo.get(school.id, school.lat, school.lon, school.rockType)
        }.getOrNull()
        val cur = _uiState.value as? SchoolDetailUiState.Success ?: return
        _uiState.value = cur.copy(monthlyStats = stats, monthlyLoading = false)
    }

    fun toggleSaveOffline() {
        val cur = _uiState.value as? SchoolDetailUiState.Success ?: return
        viewModelScope.launch {
            if (cur.isSavedOffline) {
                savedSchoolRepo.remove(cur.school.id)
                runCatching { offlineTiles.removeFor(cur.school.id) }
                _uiState.value = cur.copy(isSavedOffline = false)
            } else {
                savedSchoolRepo.saveOffline(cur.school, cur.blocks, cur.forecast)
                runCatching { offlineTiles.downloadFor(cur.school.id, cur.school.lat, cur.school.lon) }
                _uiState.value = cur.copy(isSavedOffline = true)
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
                if (!networkMonitor.isOnline.value) {
                    // Sin red → encolar; la UI muestra estado actual sin la nota nueva todavía.
                    outboxRepo.enqueue(
                        type = com.meteomontana.android.data.outbox.OutboxType.NOTE,
                        schoolId = schoolId,
                        payloadJson = kotlinx.serialization.json.Json.encodeToString(
                            com.meteomontana.android.data.api.dto.CreateNoteRequest.serializer(),
                            com.meteomontana.android.data.api.dto.CreateNoteRequest(text = text)
                        )
                    )
                    return@launch
                }
                createNote(schoolId, text)
                val notes = getNotes(schoolId)
                val cur = _uiState.value
                if (cur is SchoolDetailUiState.Success) _uiState.value = cur.copy(notes = notes)
            } catch (_: Throwable) {}
        }
    }

    suspend fun submitContribution(req: ContributionRequest): Result<Unit> {
        // Si NO hay red → encolar en outbox y devolver "éxito" para que la UI cierre.
        // Se enviará automáticamente cuando vuelva la conexión (OutboxFlusher).
        if (!networkMonitor.isOnline.value) {
            return runCatching {
                outboxRepo.enqueue(
                    type = com.meteomontana.android.data.outbox.OutboxType.CONTRIBUTION,
                    schoolId = schoolId,
                    payloadJson = kotlinx.serialization.json.Json.encodeToString(
                        ContributionRequest.serializer(), req
                    )
                )
            }
        }
        return runCatching { submitContributionUseCase(schoolId, req); Unit }
    }

    /** Admin: mueve la escuela directamente. */
    suspend fun adminMoveSchool(lat: Double, lon: Double): Result<Unit> = runCatching {
        ktorAdminApi.moveSchool(schoolId, lat, lon)
        load()  // refresca centerLat/Lon
    }

    /** Admin: mueve un bloque (piedra/parking/zona) directamente conservando el resto. */
    suspend fun adminMoveBlock(blockId: String, lat: Double, lon: Double): Result<Unit> = runCatching {
        val cur = (uiState.value as? SchoolDetailUiState.Success)
            ?.blocks?.firstOrNull { it.id == blockId }
            ?: error("Block no encontrado: $blockId")
        updateBlockUseCase(blockId, com.meteomontana.android.data.api.dto.CreateBlockRequest(
            type = cur.type, name = cur.name, lat = lat, lon = lon,
            photoPath = cur.photoPath, description = cur.description,
            lines = emptyList()
        ))
        load()
    }

    suspend fun submitBoulderContribution(
        lat: Double, lon: Double,
        name: String?,
        bloques: List<BoulderBloqueForm>,
        photoRef: FileRef?
    ): Result<Unit> = runCatching {
        val photoUrl = if (photoRef != null) {
            val bytes = fileReader.readBytes(photoRef)
            photoUploader.uploadBoulderPhoto(bytes, "image/jpeg", schoolId)
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
