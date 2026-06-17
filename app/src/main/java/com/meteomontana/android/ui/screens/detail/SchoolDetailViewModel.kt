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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        val offlineSnapshotAt: Long? = null,
        /** Si !=null, el forecast viene de la caché local y se bajó en esa fecha (epoch ms). */
        val forecastCachedAt: Long? = null
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
    private val networkMonitor: com.meteomontana.android.domain.port.NetworkMonitor,
    private val createJournalEntry: com.meteomontana.android.domain.usecase.journal.CreateJournalEntryUseCase,
    private val getMyJournal: com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase,
    private val deleteJournalEntry: com.meteomontana.android.domain.usecase.journal.DeleteJournalEntryUseCase
) : ViewModel() {

    private val schoolId: String = checkNotNull(savedStateHandle["schoolId"])

    private val journalJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    /** Diario del usuario (para marcar las vías ya hechas con ✓ persistente). */
    private val _myJournal = MutableStateFlow<List<com.meteomontana.android.domain.model.JournalSession>>(emptyList())
    val myJournal: StateFlow<List<com.meteomontana.android.domain.model.JournalSession>> = _myJournal.asStateFlow()

    /**
     * Claves "escuela|vía" de las vías HECHAS, combinando el diario (ya subidas)
     * y la cola offline pendiente (marcadas sin red, aún sin subir). Así el ✓
     * queda persistente incluso sin conexión.
     */
    val doneViaKeys: StateFlow<Set<String>> =
        kotlinx.coroutines.flow.combine(_myJournal, outboxRepo.observePending()) { journal, pending ->
            val keys = mutableSetOf<String>()
            journal.forEach { keys.add(viaKey(it.schoolId, it.blockName)) }
            pending.filter { it.type == com.meteomontana.android.data.outbox.OutboxType.JOURNAL }.forEach { row ->
                runCatching {
                    journalJson.decodeFromString(
                        com.meteomontana.android.data.api.dto.CreateJournalRequest.serializer(), row.payloadJson)
                }.getOrNull()?.let { keys.add(viaKey(it.schoolId, it.blockName)) }
            }
            keys
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private fun viaKey(schoolId: String?, name: String) =
        "${schoolId ?: ""}|${name.trim().lowercase()}"

    init { refreshJournal() }

    /** Recarga el diario (en silencio; ignora errores de red). */
    fun refreshJournal() {
        viewModelScope.launch {
            _myJournal.value = runCatching { getMyJournal() }.getOrDefault(emptyList())
        }
    }

    /**
     * Marca/DESMARCA una vía como hecha (toggle). Si no estaba hecha la añade al
     * diario (POST, o cola si no hay red); si ya estaba, la quita (borra la
     * entrada subida y/o la pendiente en la cola). Evita duplicados: si ya está
     * hecha, [toggleLine] desmarca en vez de volver a añadir. Devuelve el nuevo
     * estado (true = ahora hecha). Espejo del tic de iOS.
     */
    suspend fun toggleLine(
        block: Block,
        line: com.meteomontana.android.domain.model.BlockLine,
        index: Int,
        schoolName: String,
        sectorName: String?
    ): Result<Boolean> = runCatching {
        val viaName = line.name.ifBlank { "Vía ${index + 1}" }
        val key = viaKey(block.schoolId, viaName)
        if (doneViaKeys.value.contains(key)) {
            // DESMARCAR: quitar de la cola pendiente (sin subir) y borrar la subida.
            outboxRepo.all()
                .filter { it.type == com.meteomontana.android.data.outbox.OutboxType.JOURNAL }
                .forEach { row ->
                    val match = runCatching {
                        journalJson.decodeFromString(
                            com.meteomontana.android.data.api.dto.CreateJournalRequest.serializer(), row.payloadJson)
                    }.getOrNull()?.let { viaKey(it.schoolId, it.blockName) == key } ?: false
                    if (match) outboxRepo.delete(row.id)
                }
            _myJournal.value.firstOrNull { viaKey(it.schoolId, it.blockName) == key }?.let {
                runCatching { deleteJournalEntry(it.id) }
            }
            refreshJournal()
            false
        } else {
            // MARCAR: crear (o encolar sin red). Dedup garantizado: no estaba hecha.
            val stoneName = block.name.ifBlank { "Piedra" }
            val req = com.meteomontana.android.data.api.dto.CreateJournalRequest(
                schoolId = block.schoolId,
                schoolName = schoolName.ifBlank { null },
                sector = sectorName,
                blockName = viaName,
                grade = line.grade,
                notes = "Piedra: $stoneName",
                date = java.time.LocalDate.now().toString()
            )
            val sent = networkMonitor.isOnline.value && runCatching { createJournalEntry(req) }.isSuccess
            if (sent) {
                refreshJournal()
            } else {
                outboxRepo.enqueue(
                    com.meteomontana.android.data.outbox.OutboxType.JOURNAL,
                    block.schoolId,
                    journalJson.encodeToString(
                        com.meteomontana.android.data.api.dto.CreateJournalRequest.serializer(), req)
                )
            }
            true
        }
    }

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
                // Llamadas independientes en paralelo: en serie eran ~5 round-trips
                // al backend (~350 ms cada uno en remoto). runCatching dentro de
                // cada async evita que un fallo individual cancele al resto.
                val success = coroutineScope {
                    val forecastD = async { runCatching { getForecast(schoolId) } }
                    val notesD = async { runCatching { getNotes(schoolId) }.getOrDefault(emptyList()) }
                    val isFavD = async { runCatching { getMyFavorites().any { it.id == schoolId } }.getOrDefault(false) }
                    val blocksD = async { runCatching { getBlocks(schoolId) }.getOrDefault(emptyList()) }
                    val isAdminD = async { runCatching { getMyProfile().isAdmin }.getOrDefault(false) }
                    val isSavedD = async { runCatching { savedSchoolRepo.loadOffline(schoolId) != null }.getOrDefault(false) }
                    val forecastResult = forecastD.await()
                    // Forecast fresco → a la caché. Si la red falló → último
                    // forecast cacheado, marcando su antigüedad para que la UI
                    // avise de que son datos viejos.
                    var forecast = forecastResult.getOrNull()
                    var forecastCachedAt: Long? = null
                    if (forecast != null) {
                        runCatching { savedSchoolRepo.cacheForecast(schoolId, forecast) }
                    } else {
                        runCatching { savedSchoolRepo.loadCachedForecast(schoolId) }.getOrNull()?.let { (cached, at) ->
                            forecast = cached
                            forecastCachedAt = at
                        }
                    }
                    SchoolDetailUiState.Success(
                        school = school,
                        forecast = forecast,
                        forecastError = if (forecast == null)
                            forecastResult.exceptionOrNull()?.toUserMessage() else null,
                        notes = notesD.await(), isFavorite = isFavD.await(), blocks = blocksD.await(),
                        isCurrentUserAdmin = isAdminD.await(),
                        isSavedOffline = isSavedD.await(),
                        monthlyLoading = true,
                        forecastCachedAt = forecastCachedAt
                    )
                }
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

    fun publishNote(text: String, photoRef: FileRef? = null) {
        viewModelScope.launch {
            try {
                if (!networkMonitor.isOnline.value) {
                    // Sin red → encolar solo el texto (subir la foto a Storage
                    // requiere conexión); la UI muestra estado actual sin la nota.
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
                // Foto → Firebase Storage (comprimida); al backend solo va la URL.
                val photoUrl = photoRef?.let {
                    val bytes = fileReader.readImageCompressed(it)
                    photoUploader.uploadNotePhoto(bytes, "image/jpeg", schoolId)
                }
                createNote(schoolId, text, photoUrl)
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
        photoRef: FileRef?,
        sectorBlockId: String? = null
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
            correctionReason = null, targetBlockId = null, targetLineId = null,
            sectorBlockId = sectorBlockId,
            photoUrl = photoUrl,
            bloquesJson = bloques.toBloquesJson(),
            topoLinesJson = null
        )
        submitContributionUseCase(schoolId, req)
        Unit
    }

    /** Propone asignar un sector (ZONE) existente a una piedra (BLOCK) existente. */
    suspend fun submitAssignSectorContribution(
        targetBlockId: String,
        targetLat: Double,
        targetLon: Double,
        sectorBlockId: String
    ): Result<Unit> = runCatching {
        val req = ContributionRequest(
            type = "ASSIGN_SECTOR",
            name = null,
            lat = targetLat,
            lon = targetLon,
            notes = null,
            description = null,
            proposedLat = null, proposedLon = null,
            correctionReason = null,
            targetBlockId = targetBlockId,
            targetLineId = null,
            sectorBlockId = sectorBlockId,
            photoUrl = null,
            bloquesJson = null,
            topoLinesJson = null
        )
        android.util.Log.d("AssignSector",
            "→ POST schoolId=$schoolId targetBlockId=$targetBlockId sectorBlockId=$sectorBlockId")
        try {
            submitContributionUseCase(schoolId, req)
            android.util.Log.d("AssignSector", "← OK")
        } catch (t: Throwable) {
            android.util.Log.e("AssignSector", "← FAIL", t)
            throw t
        }
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
            targetLineId = null,
            photoUrl = null,
            bloquesJson = bloques.toBloquesJson(),
            topoLinesJson = null
        )
        submitContributionUseCase(schoolId, req)
        Unit
    }

    /** Corrección de una vía existente: actualiza nombre/grado/tipo/línea de targetLineId. */
    suspend fun submitEditLineContribution(
        targetBlockId: String,
        targetLineId: String,
        targetLat: Double,
        targetLon: Double,
        bloque: BoulderBloqueForm
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
            targetLineId = targetLineId,
            photoUrl = null,
            bloquesJson = listOf(bloque).toBloquesJson(),
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
