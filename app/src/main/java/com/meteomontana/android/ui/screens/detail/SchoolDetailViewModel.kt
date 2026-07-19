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
import com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
import com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
import com.meteomontana.android.domain.usecase.notes.CreateNoteUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
        val forecastCachedAt: Long? = null,
        /** Boletín de montaña AEMET si la escuela cae en uno de los 9 macizos. */
        val mountainBulletin: com.meteomontana.android.data.api.MountainBulletinDto? = null
    ) : SchoolDetailUiState
}

/**
 * FACHADA del detalle de escuela: publica el estado y delega el trabajo en sus
 * colaboradores (SRP — antes esta clase hacía las 4 cosas ella misma, ~1000
 * líneas y 28 dependencias):
 *  - [SchoolDetailLoader]     → carga paralela + fallback offline
 *  - [JournalTickController]  → ✓ hecho/proyecto del diario + cola offline
 *  - [SchoolContributionSender] → las variantes de proponer/corregir
 *  - [TickFeedPublisher]      → publicar el tick en el feed Comunidad
 * La API pública hacia la UI no cambia.
 */
@HiltViewModel
class SchoolDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loader: SchoolDetailLoader,
    private val journal: JournalTickController,
    private val contributions: SchoolContributionSender,
    private val tickFeed: TickFeedPublisher,
    private val getNotes: GetNotesUseCase,
    private val createNote: CreateNoteUseCase,
    private val addFavorite: AddFavoriteUseCase,
    private val removeFavorite: RemoveFavoriteUseCase,
    private val getBlocks: GetBlocksUseCase,
    private val createBlock: CreateBlockUseCase,
    private val deleteBlockUseCase: DeleteBlockUseCase,
    private val photoUploader: PhotoUploader,
    private val fileReader: FileReader,
    private val monthlyStatsRepo: MonthlyStatsRepository,
    private val savedSchoolRepo: SavedSchoolRepository,
    private val offlineTiles: com.meteomontana.android.data.map.OfflineTileManager,
    private val ktorAdminApi: com.meteomontana.android.data.api.KtorAdminApi,
    private val updateBlockUseCase: com.meteomontana.android.domain.usecase.blocks.UpdateBlockUseCase,
    private val outboxRepo: com.meteomontana.android.data.outbox.OutboxRepository,
    private val noteApi: com.meteomontana.android.data.api.KtorNoteApi,
    private val networkMonitor: com.meteomontana.android.domain.port.NetworkMonitor,
    private val rateLineUseCase: com.meteomontana.android.domain.usecase.blocks.RateLineUseCase
) : ViewModel() {

    private val schoolId: String = checkNotNull(savedStateHandle["schoolId"])

    // ── Deep-links ─────────────────────────────────────────────────────────

    // Deep-link desde el diario: vía a abrir (se despliega el mapa y se abre la
    // piedra que la contiene). One-shot: se consume al abrirla.
    private val _autoOpenVia = MutableStateFlow(
        savedStateHandle.get<String>("via")?.takeIf { it.isNotBlank() }
    )
    val autoOpenVia: StateFlow<String?> = _autoOpenVia.asStateFlow()
    fun consumeAutoOpenVia() { _autoOpenVia.value = null; _autoOpenViaId.value = null }

    /** Buscador de vías/bloques del detalle: abre la piedra que la contiene. */
    fun openVia(lineId: String?, name: String?) {
        _autoOpenViaId.value = lineId
        _autoOpenVia.value = name
    }

    // Deep-link por id ESTABLE de la vía (Fase 8): preferente sobre el nombre —
    // aguanta renombres, reordenes y muros. null = deep-link antiguo por nombre.
    private val _autoOpenViaId = MutableStateFlow(
        savedStateHandle.get<String>("viaId")?.takeIf { it.isNotBlank() }
    )
    val autoOpenViaId: StateFlow<String?> = _autoOpenViaId.asStateFlow()

    // Deep-link por id de PIEDRA (posts "piedra nueva" del feed, sin vía):
    // abre la ficha de esa piedra directamente.
    private val _autoOpenBlockId = MutableStateFlow(
        savedStateHandle.get<String>("blockId")?.takeIf { it.isNotBlank() }
    )
    val autoOpenBlockId: StateFlow<String?> = _autoOpenBlockId.asStateFlow()
    fun consumeAutoOpenBlock() { _autoOpenBlockId.value = null }

    // ── Diario ✓ hecho/proyecto (delegado en JournalTickController) ────────

    val myJournal: StateFlow<List<com.meteomontana.android.domain.model.JournalSession>> = journal.myJournal

    val doneViaKeys: StateFlow<Set<String>> =
        journal.doneViaKeys().stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val projectViaKeys: StateFlow<Set<String>> =
        journal.projectViaKeys().stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun refreshJournal() {
        viewModelScope.launch { journal.refresh() }
    }

    /** Marca/DESMARCA una vía como hecha. Ver [JournalTickController.toggleLine]. */
    suspend fun toggleLine(
        block: Block,
        line: com.meteomontana.android.domain.model.BlockLine,
        index: Int,
        schoolName: String,
        sectorName: String?,
        markDone: Boolean? = null
    ): Result<Boolean> = journal.toggleLine(
        block, line, index, schoolName, sectorName, markDone,
        doneViaKeys.value, projectViaKeys.value
    )

    /** Marca/DESMARCA una vía como PROYECTO. Ver [JournalTickController.toggleProject]. */
    suspend fun toggleProject(
        block: Block,
        line: com.meteomontana.android.domain.model.BlockLine,
        index: Int,
        schoolName: String,
        sectorName: String?,
        markProject: Boolean? = null
    ): Result<Boolean> = journal.toggleProject(
        block, line, index, schoolName, sectorName, markProject,
        doneViaKeys.value, projectViaKeys.value
    )

    /** Publica el tick en el feed Comunidad (fire-and-forget). */
    fun publishTickToFeed(
        block: Block,
        line: com.meteomontana.android.domain.model.BlockLine,
        wasProject: Boolean,
        caption: String? = null,
        photoUri: String? = null,
        onPhotoUploadFailed: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            tickFeed.publish(block, line, wasProject, caption, photoUri, onPhotoUploadFailed)
        }
    }

    // ── Carga del detalle ──────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<SchoolDetailUiState>(SchoolDetailUiState.Loading)
    val uiState: StateFlow<SchoolDetailUiState> = _uiState.asStateFlow()

    init {
        refreshJournal()
        load()
    }

    fun load() {
        _uiState.value = SchoolDetailUiState.Loading
        viewModelScope.launch {
            val result = loader.load(schoolId)
            _uiState.value = result.state
            val success = result.state as? SchoolDetailUiState.Success ?: return@launch
            if (!result.fromNetwork) return@launch  // snapshot offline: sin cargas extra
            viewModelScope.launch { loadMonthlyStats(success.school) }
            // Si el sitio está guardado offline, refresca su snapshot con lo
            // recién bajado (bloques + forecast) para que SIN conexión nunca
            // se vean datos viejos tras una modificación. Paridad con iOS.
            if (success.isSavedOffline && success.blocks.isNotEmpty()) {
                viewModelScope.launch {
                    runCatching {
                        savedSchoolRepo.saveOffline(success.school, success.blocks, success.forecast)
                    }
                }
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

    // ── Notas ──────────────────────────────────────────────────────────────

    /** Voto de utilidad en una nota (1/-1; repetir retira). Actualiza en local. */
    fun voteNote(note: com.meteomontana.android.domain.model.Note, value: Int) {
        viewModelScope.launch {
            val newVote = runCatching { noteApi.voteNote(note.id, value) }.getOrNull() ?: return@launch
            (_uiState.value as? SchoolDetailUiState.Success)?.let { s ->
                _uiState.value = s.copy(notes = s.notes.map { nte ->
                    if (nte.id != note.id) nte else {
                        val dUp = (if (newVote == 1) 1 else 0) - (if (nte.myVote == 1) 1 else 0)
                        val dDown = (if (newVote == -1) 1 else 0) - (if (nte.myVote == -1) 1 else 0)
                        nte.copy(upvotesCount = nte.upvotesCount + dUp,
                            downvotesCount = nte.downvotesCount + dDown, myVote = newVote)
                    }
                })
            }
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

    // ── Guardado offline / favoritas ───────────────────────────────────────

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

    // ── Contribuciones (delegadas en SchoolContributionSender) ─────────────

    suspend fun submitContribution(req: ContributionRequest): Result<Unit> =
        contributions.submit(schoolId, req)

    suspend fun queueContributionOffline(req: ContributionRequest) =
        contributions.queueOffline(schoolId, req)

    suspend fun queueBoulderOffline(
        lat: Double, lon: Double, name: String?,
        sectorBlockId: String?, discipline: String, geometry: String,
        pathJson: String?, direction: String,
        faces: List<com.meteomontana.android.data.outbox.QueuedFace>
    ) = contributions.queueBoulderOffline(
        schoolId, lat, lon, name, sectorBlockId, discipline, geometry,
        pathJson, direction, faces
    )

    suspend fun submitBoulderContribution(
        lat: Double, lon: Double,
        name: String?,
        bloques: List<BoulderBloqueForm>,
        photoRef: FileRef?,
        sectorBlockId: String? = null
    ): Result<Unit> = contributions.submitBoulder(schoolId, lat, lon, name, bloques, photoRef, sectorBlockId)

    suspend fun submitBoulderFacesContribution(
        lat: Double, lon: Double,
        name: String?,
        faces: List<BoulderFaceForm>,
        sectorBlockId: String? = null,
        discipline: String = "BOULDER",
        geometry: String = "POINT",
        path: String? = null,
        direction: String = "LTR"
    ): Result<Unit> = contributions.submitBoulderFaces(
        schoolId, lat, lon, name, faces, sectorBlockId, discipline, geometry, path, direction
    )

    suspend fun submitAssignSectorContribution(
        targetBlockId: String,
        targetLat: Double,
        targetLon: Double,
        sectorBlockId: String
    ): Result<Unit> = contributions.submitAssignSector(schoolId, targetBlockId, targetLat, targetLon, sectorBlockId)

    suspend fun submitAddLinesContribution(
        targetBlockId: String,
        targetLat: Double,
        targetLon: Double,
        bloques: List<BoulderBloqueForm>
    ): Result<Unit> = contributions.submitAddLines(schoolId, targetBlockId, targetLat, targetLon, bloques)

    /** Sube una foto de piedra (cara nueva) y devuelve su URL. */
    suspend fun uploadBoulderPhoto(ref: FileRef): Result<String> =
        contributions.uploadBoulderPhoto(schoolId, ref)

    suspend fun submitBoulderCorrections(
        targetBlockId: String,
        targetLat: Double,
        targetLon: Double,
        bloques: List<BoulderBloqueForm>,
        geometry: String = "POINT",
        path: String? = null,
        direction: String = "LTR"
    ): Result<Unit> = contributions.submitBoulderCorrections(
        schoolId, targetBlockId, targetLat, targetLon, bloques, geometry, path, direction
    )

    suspend fun submitEditLineContribution(
        targetBlockId: String,
        targetLineId: String,
        targetLat: Double,
        targetLon: Double,
        bloque: BoulderBloqueForm
    ): Result<Unit> = contributions.submitEditLine(
        schoolId, targetBlockId, targetLineId, targetLat, targetLon, bloque
    )

    // ── Admin / bloques ────────────────────────────────────────────────────

    /** Admin: mueve la escuela directamente. */
    suspend fun adminMoveSchool(lat: Double, lon: Double): Result<Unit> = runCatching {
        ktorAdminApi.moveSchool(schoolId, lat, lon)
        load()  // refresca centerLat/Lon
    }

    /** Admin: edita nombre/descripción/coords de un bloque (mini-ficha del mapa). */
    suspend fun adminUpdateBlock(
        blockId: String, req: com.meteomontana.android.data.api.dto.CreateBlockRequest
    ): Result<Unit> = runCatching {
        updateBlockUseCase(blockId, req)
        load()
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

    /** Valora una vía (1-5 estrellas). Devuelve el resultado actualizado o null si falla. */
    suspend fun rateLine(blockId: String, lineId: String, stars: Int) = runCatching {
        rateLineUseCase.rate(blockId, lineId, stars)
    }

    /** Borra la valoración de una vía. */
    suspend fun unrateLine(blockId: String, lineId: String) = runCatching {
        rateLineUseCase.unrate(blockId, lineId)
    }
}

/**
 * Clave de diario de una vía: "escuela|#lineId" si tiene id (aguanta
 * homónimas), "escuela|nombre" como legado si no. ÚNICA fuente del formato —
 * la usan el ViewModel y la UI (SchoolMap) para que siempre casen.
 */
fun journalViaKey(schoolId: String?, lineId: String?, viaName: String): String =
    if (!lineId.isNullOrBlank()) "${schoolId ?: ""}|#$lineId"
    else "${schoolId ?: ""}|${viaName.trim().lowercase()}"

/**
 * Ids de las vías de [block] cuyo diario casa con alguna clave de [keys]:
 * por lineId ("escuela|#id", aguanta homónimas — fix "La ola") o por nombre
 * como LEGADO (entradas antiguas sin lineId). Es la traducción diario → ✓ de
 * la ficha de piedra; función PURA para testearla sin Compose.
 */
fun matchedLineIds(
    block: com.meteomontana.android.domain.model.Block,
    keys: Set<String>
): Set<String> =
    block.lines.mapIndexedNotNull { idx, line ->
        val viaName = line.name.ifBlank { "Vía ${idx + 1}" }
        val idKey = journalViaKey(block.schoolId, line.id.takeIf { it.isNotBlank() }, viaName)
        val nameKey = "${block.schoolId}|${viaName.trim().lowercase()}"
        if (keys.contains(idKey) || keys.contains(nameKey)) line.id else null
    }.toSet()
