package com.meteomontana.android.ui.screens.detail

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.journal.journalViaKey
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.port.NetworkMonitor
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.schools.ExploreLinesUseCase
import com.meteomontana.android.domain.usecase.schools.GuideSector
import com.meteomontana.android.domain.usecase.schools.GuideView
import com.meteomontana.android.domain.usecase.schools.LinesFilter
import com.meteomontana.android.domain.usecase.schools.LinesOrder
import com.meteomontana.android.domain.usecase.schools.LoadBetaLineIdsUseCase
import com.meteomontana.android.domain.usecase.schools.LoadSchoolLinesUseCase
import com.meteomontana.android.domain.usecase.schools.SchoolGuide
import com.meteomontana.android.domain.usecase.schools.SchoolLinesExplorer
import com.meteomontana.android.ui.screens.detail.guide.GuideMapSnapshots
import com.meteomontana.android.ui.screens.detail.guide.GuideOverview
import com.meteomontana.android.ui.screens.detail.guide.GuidePdf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la pestaña BLOQUES/VÍAS de la ficha de escuela, en forma de GUÍA. Vive en el ViewModel (no en la pestaña)
 * para que al ir al mapa o al tiempo y volver no se pierdan los filtros ni se vuelva a bajar la lista. Espejo de
 * `SchoolBlocksExplorerModel` (iOS).
 *
 * SOLO guarda estado de pantalla y descarga datos con los use cases. Filtrar, ordenar, las destacadas y agrupar por
 * sector es lógica de dominio y vive en [SchoolLinesExplorer] y [SchoolGuide] (shared, con tests): iOS y Android
 * hacen lo mismo. El mapa y el PDF los dibujan [GuideMapSnapshots] y [GuidePdf].
 */
@HiltViewModel
class SchoolBlocksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exploreLines: ExploreLinesUseCase,
    private val getBlocks: GetBlocksUseCase,
    private val network: NetworkMonitor,
    private val maps: GuideMapSnapshots,
    private val pdf: GuidePdf,
    @ApplicationContext context: Context
) : ViewModel() {

    private val schoolId: String = checkNotNull(savedStateHandle["schoolId"])
    private val loadLines = LoadSchoolLinesUseCase(exploreLines)
    private val loadBeta = LoadBetaLineIdsUseCase(exploreLines)
    private val prefs = context.getSharedPreferences("guide_bookmarks", Context.MODE_PRIVATE)

    enum class Order(val shared: LinesOrder) { Repeated(LinesOrder.MOST_REPEATED), Rated(LinesOrder.BEST_RATED) }

    data class UiState(
        val loading: Boolean = true,
        val failed: Boolean = false,
        val all: List<LineSearchHit> = emptyList(),
        val query: String = "",
        val grades: Set<String> = emptySet(),
        /** Alturas de beta marcadas. Vacío = sin filtrar; las dos = cualquier vía con beta. */
        val betas: Set<String> = emptySet(),
        val betaLineIds: Map<String, Set<String>> = emptyMap(),
        /** QUÉ BUSCAS con la altura: "" vídeo o encadenes, "_VIDEO" solo vídeo, "_SENDS" solo encadenes. */
        val betaSource: String = "",
        val order: Order = Order.Repeated,
        /** Caras marcadas (N/E/S/O). Valen para las destacadas y la guía. */
        val faces: Set<String> = emptySet(),
        /** Solo lo que NO tienes hecho en tu diario. */
        val onlyNotDone: Boolean = false,
        /** Vías hechas / en proyecto según TU diario (lo da la ficha, que ya lo vigila). */
        val doneIds: Set<String> = emptySet(),
        val projectIds: Set<String> = emptySet(),
        /** Sectores DESPLEGADOS de la guía (al cargar, solo el primero). */
        val guideOpen: Set<String> = emptySet(),
        /** Panel "Más" (grado y beta): plegado por defecto para que se vea antes la guía. */
        val filtersOpen: Boolean = false,
        /** Mapa de la escuela desplegado en la guía (plegado al entrar). */
        val mapExpanded: Boolean = false,
        /** Mapa general como imagen (la copia guardada sale primero; con red se regenera). */
        val overview: GuideOverview? = null,
        /** Piedras, parkings y sectores de la escuela, para el mapa en vivo y las fotos aéreas de sector. */
        val overviewBlocks: List<Block> = emptyList(),
        /** Foto aérea de cada sector (clave → imagen). */
        val sectorMaps: Map<String, Bitmap> = emptyMap(),
        /** Sectores cuya foto aérea ya se intentó (con o sin éxito): hasta entonces se enseña un hueco, no otra foto. */
        val sectorMapsTried: Set<String> = emptySet(),
        val makingPdf: Boolean = false
    ) {
        val hasLines: Boolean get() = all.any { it.lineId != null }
        val gradeOptions: List<String> get() = SchoolLinesExplorer.gradeOptions(all)

        private val filter: LinesFilter
            get() = LinesFilter(query, grades, betas, order.shared, faces, if (onlyNotDone) doneIds else emptySet())

        /** Destacadas (más repetidas / mejor valoradas) con los mismos filtros; null = bajando beta. */
        val highlights: List<LineSearchHit>? get() = SchoolLinesExplorer.highlights(all, filter, betaLineIds, 10)

        /** La guía con los mismos filtros; null = bajando beta. */
        val guideView: GuideView? get() = SchoolGuide.view(all, filter, betaLineIds)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Saltos que pide la guía (a un sector o a una piedra): la ficha los consume y baja hasta allí. */
    sealed interface Jump {
        data class Sector(val key: String) : Jump
        data class Block(val blockId: String) : Jump
    }
    private val _jumps = Channel<Jump>(Channel.CONFLATED)
    val jumps = _jumps.receiveAsFlow()

    /** El PDF listo para compartir (o null si falló). */
    private val _pdfReady = Channel<File?>(Channel.BUFFERED)
    val pdfReady = _pdfReady.receiveAsFlow()

    private var loaded = false

    /** Baja las vías de la escuela UNA vez (los filtros se aplican en local). `force` para reintentar o recargar. */
    fun loadIfNeeded(force: Boolean = false) {
        if (loaded && !force && !_state.value.failed) return
        if (!loaded) _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                val lines = loadLines(schoolId)
                val first = if (loaded) null else SchoolGuide.sectors(lines).firstOrNull()?.key
                loaded = true
                _state.update {
                    it.copy(all = lines, loading = false, failed = false,
                        guideOpen = if (first != null) setOf(first) else it.guideOpen)
                }
                applyJournal()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!loaded) _state.update { it.copy(loading = false, failed = true) }
            }
        }
        loadOverview()
    }

    /** Mapa general: primero la copia de disco, luego (con red) uno nuevo. */
    private fun loadOverview() = viewModelScope.launch {
        if (_state.value.overview == null) maps.cached(schoolId)?.let { ov -> _state.update { it.copy(overview = ov) } }
        val blocks = runCatching { getBlocks(schoolId) }.getOrNull() ?: return@launch
        _state.update { it.copy(overviewBlocks = blocks) }
        if (!network.isOnline.value) return@launch
        maps.generate(schoolId, blocks)?.let { ov -> _state.update { it.copy(overview = ov) } }
    }

    /** Genera (o lee de disco) la foto aérea de un sector con SUS piedras. */
    fun loadSectorMap(sector: GuideSector) {
        if (sector.key in _state.value.sectorMaps) return
        viewModelScope.launch { fetchSectorMap(sector) }
    }

    private suspend fun fetchSectorMap(sector: GuideSector) {
        if (sector.key in _state.value.sectorMaps) return
        val ids = sector.blocks.map { it.blockId }.toSet()
        val stones = _state.value.overviewBlocks.filter { it.id in ids }
        val img = maps.sectorMap(schoolId, sector.key, stones)
        _state.update {
            it.copy(sectorMaps = if (img != null) it.sectorMaps + (sector.key to img) else it.sectorMaps,
                sectorMapsTried = it.sectorMapsTried + sector.key)
        }
    }

    val isOnline: Boolean get() = network.isOnline.value

    /** Genera el PDF con los filtros de ahora. [sectorKey] null = toda la guía. */
    fun exportPdf(schoolName: String, sectorKey: String?) {
        val s = _state.value
        if (s.makingPdf) return
        val g = s.guideView ?: return
        _state.update { it.copy(makingPdf = true) }
        viewModelScope.launch {
            val file = try {
                for (sec in g.sectors) if (sectorKey == null || sec.key == sectorKey) fetchSectorMap(sec)
                pdf.make(schoolName, g.sectors, _state.value.overview, _state.value.sectorMaps, sectorKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update { it.copy(makingPdf = false) }
            _pdfReady.send(file)
        }
    }

    /** El diario (claves "escuela|#vía") que vigila la ficha → qué vías de esta escuela tienes hechas o en proyecto. */
    fun setJournal(doneKeys: Set<String>, projectKeys: Set<String>) {
        journalKeys = doneKeys to projectKeys
        applyJournal()
    }

    private var journalKeys: Pair<Set<String>, Set<String>> = emptySet<String>() to emptySet()

    /** Se recalcula al cambiar el diario y al llegar las vías (pueden llegar en cualquier orden). */
    private fun applyJournal() {
        val (doneKeys, projectKeys) = journalKeys
        val all = _state.value.all
        val done = HashSet<String>(); val project = HashSet<String>()
        for (h in all) {
            val id = h.lineId ?: continue
            val key = journalViaKey(schoolId, id, h.lineName ?: h.blockName)
            if (key in doneKeys) done.add(id) else if (key in projectKeys) project.add(id)
        }
        _state.update { it.copy(doneIds = done, projectIds = project) }
    }

    // ── Filtros y plegados ──

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun clearGrades() = _state.update { it.copy(grades = emptySet()) }
    fun toggleGrade(g: String) = _state.update { it.copy(grades = if (g in it.grades) it.grades - g else it.grades + g) }
    fun clearBetas() = _state.update { it.copy(betas = emptySet()) }
    fun setOrder(o: Order) = _state.update { it.copy(order = o) }
    fun toggleFilters() = _state.update { it.copy(filtersOpen = !it.filtersOpen) }
    fun toggleMap() = _state.update { it.copy(mapExpanded = !it.mapExpanded) }
    fun toggleFace(f: String) = _state.update { it.copy(faces = if (f in it.faces) it.faces - f else it.faces + f) }
    fun clearFaces() = _state.update { it.copy(faces = emptySet()) }
    fun toggleNotDone() = _state.update { it.copy(onlyNotDone = !it.onlyNotDone) }
    fun toggleSector(key: String) =
        _state.update { it.copy(guideOpen = if (key in it.guideOpen) it.guideOpen - key else it.guideOpen + key) }

    /** Toca un sector (índice o mapa): se despliega y la ficha baja hasta él. */
    fun jumpToSector(key: String) {
        _state.update { it.copy(guideOpen = it.guideOpen + key) }
        _jumps.trySend(Jump.Sector(key))
    }

    /** Lo mismo desde el mapa, que solo sabe el NOMBRE del sector. */
    fun jumpToSectorNamed(name: String) {
        _state.value.guideView?.sectors?.firstOrNull { it.sector == name }?.let { jumpToSector(it.key) }
    }

    /** Toca una vía del inicio de sector: baja hasta la tarjeta de su piedra. */
    fun jumpToBlock(sectorKey: String, blockId: String) {
        setBookmark(blockId)
        _state.update { it.copy(guideOpen = it.guideOpen + sectorKey) }
        _jumps.trySend(Jump.Block(blockId))
    }

    // ── Marcapáginas ──

    /** Última piedra con la que hiciste algo en la guía; al volver a la pestaña se salta a ella. Por escuela. */
    fun setBookmark(blockId: String) = prefs.edit().putString(schoolId, blockId).apply()

    /** Despliega el sector de la piedra marcada (si sale con los filtros de ahora) y pide bajar hasta ella. */
    fun jumpToBookmark() {
        val id = prefs.getString(schoolId, null) ?: return
        val s = _state.value.guideView?.sectors?.firstOrNull { sec -> sec.blocks.any { it.blockId == id } } ?: return
        _state.update { it.copy(guideOpen = it.guideOpen + s.key) }
        _jumps.trySend(Jump.Block(id))
    }

    /** Los enlaces de beta viven en el servidor: se piden por altura y se guardan. */
    fun toggleBeta(height: String) {
        val on = height in _state.value.betas
        _state.update { it.copy(betas = if (on) it.betas - height else it.betas + height) }
        if (on || _state.value.betaLineIds.containsKey(height)) return
        loadBetaIds(height)
    }

    /** Cambia QUÉ BUSCAS: las vías de cada altura marcada se vuelven a pedir con la fuente nueva. */
    fun setBetaSource(source: String) {
        if (source == _state.value.betaSource) return
        _state.update { it.copy(betaSource = source, betaLineIds = emptyMap()) }
        _state.value.betas.forEach(::loadBetaIds)
    }

    private fun loadBetaIds(height: String) {
        val source = _state.value.betaSource
        viewModelScope.launch {
            try {
                val ids = loadBeta(schoolId, height + source)
                if (_state.value.betaSource != source) return@launch   // llegó tarde: ya se cambió la fuente
                _state.update { it.copy(betaLineIds = it.betaLineIds + (height to ids)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin conexión a medias: se quita el filtro en vez de dejar la lista vacía sin explicación.
                _state.update { it.copy(betas = it.betas - height) }
            }
        }
    }
}
