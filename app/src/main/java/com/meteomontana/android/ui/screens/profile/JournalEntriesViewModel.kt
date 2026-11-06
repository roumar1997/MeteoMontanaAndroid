package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.domain.model.AscentStyle
import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonGradeRow
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.width
import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.util.AppText
import androidx.compose.runtime.setValue
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.usecase.journal.DeleteJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalStatsUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserStatsUseCase
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Estado y ViewModel de la lista del diario (separados de la pantalla).

sealed interface JournalEntriesUiState {
    data object Loading : JournalEntriesUiState
    data class Success(
        val entries: List<JournalSession>,
        val filter: String?,
        val isMine: Boolean,
        // entryId -> (nº piedra, sector) resueltos del catálogo en vivo.
        val viaInfo: Map<String, com.meteomontana.android.domain.usecase.journal.ViaCatalogInfo> = emptyMap()
    ) : JournalEntriesUiState
    data class Error(val message: String) : JournalEntriesUiState
}

@HiltViewModel
class JournalEntriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMyJournal: GetMyJournalUseCase,
    private val getMyJournalStats: GetMyJournalStatsUseCase,
    private val getUserJournal: GetUserJournalUseCase,
    private val getUserStats: GetUserStatsUseCase,
    private val deleteJournalEntry: DeleteJournalEntryUseCase,
    private val updateJournalDate: com.meteomontana.android.domain.usecase.journal.UpdateJournalDateUseCase,
    private val updateJournalStyle: com.meteomontana.android.domain.usecase.journal.UpdateJournalStyleUseCase,
    private val getJournalViaInfo: com.meteomontana.android.domain.usecase.journal.GetJournalViaInfoUseCase,
    private val outboxRepo: com.meteomontana.android.data.outbox.OutboxRepository
) : ViewModel() {
    private val filter: String? = savedStateHandle["filter"]
    private val uid: String? = savedStateHandle.get<String>("uid")?.takeIf { it.isNotBlank() }
    private val isMine: Boolean = uid == null

    private val _state = MutableStateFlow<JournalEntriesUiState>(JournalEntriesUiState.Loading)
    val state: StateFlow<JournalEntriesUiState> = _state.asStateFlow()

    // Mensaje de error del ÚLTIMO cambio de estilo, con el detalle real (código
    // HTTP incluido) en vez de un aviso genérico — para poder diagnosticar sin
    // logs si vuelve a fallar (Rodrigo, 2026-08-22).
    private val _styleError = MutableStateFlow<String?>(null)
    val styleError: StateFlow<String?> = _styleError.asStateFlow()
    fun clearStyleError() { _styleError.value = null }

    // Filtro compuesto "school:X|sector:Y" (llegando desde JournalSectorsScreen):
    // el título es el nombre del sector, no de la escuela (ya viniste de ahí).
    private val sectorName: String? = filter
        ?.takeIf { it.contains("|sector:") }
        ?.substringAfter("|sector:")

    val title: String = when {
        filter == null               -> if (isMine) AppText.get(R.string.journal_entries_screen_v4_todos_mis_bloques) else AppText.get(R.string.journal_entries_screen_v4_todos_los_bloques)
        sectorName != null           -> sectorName
        filter.startsWith("school:") -> filter.removePrefix("school:")
        filter == "grade-max"        -> AppText.get(R.string.journal_entries_screen_v4_grado_maximo)
        filter == "discipline:BOULDER" -> if (isMine) AppText.get(R.string.journal_entries_screen_v4_mis_bloques) else "Bloques"
        filter == "discipline:ROUTE"   -> if (isMine) AppText.get(R.string.journal_entries_screen_v4_mis_vias) else AppText.get(R.string.journal_entries_screen_v4_vias)
        filter == "project"          -> if (isMine) AppText.get(R.string.journal_entries_screen_v4_mis_proyectos) else "Proyectos"
        filter == "project:BOULDER"  -> if (isMine) AppText.get(R.string.journal_entries_screen_v4_mis_proyectos_bloques) else AppText.get(R.string.journal_entries_screen_v4_proyectos_bloques)
        filter == "project:ROUTE"    -> if (isMine) AppText.get(R.string.journal_entries_screen_v4_mis_proyectos_vias) else AppText.get(R.string.journal_entries_screen_v4_proyectos_vias)
        else                         -> AppText.get(R.string.w_logbook)
    }

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                val server = if (uid == null) getMyJournal() else getUserJournal(uid)
                // En mi propio diario, descuento las vías con BORRADO pendiente en
                // la cola offline (desmarcadas sin red): si no, seguían apareciendo
                // hasta sincronizar. Espejo de doneViaKeys en el detalle.
                val visible = if (isMine) {
                    val pendingDeletes = outboxRepo.all()
                        .filter { it.type == com.meteomontana.android.data.outbox.OutboxType.JOURNAL_DELETE }
                        .map { it.payloadJson }.toSet()
                    if (pendingDeletes.isEmpty()) server
                    else server.filter {
                        // El payload del borrado puede ser clave por lineId
                        // ("escuela|#id") o por nombre (legado) — probar ambas.
                        val idKey = it.lineId?.takeIf { l -> l.isNotBlank() }
                            ?.let { l -> "${it.schoolId ?: ""}|#$l" }
                        val nameKey = "${it.schoolId ?: ""}|${it.blockName.trim().lowercase()}"
                        idKey !in pendingDeletes && nameKey !in pendingDeletes
                    }
                } else server
                // Sin duplicados: la misma vía marcada varias veces se colapsa en
                // una (la más reciente). Por lineId cuando lo hay (dos homónimas
                // son entradas DISTINTAS); por escuela|sector|nombre como legado.
                val all = visible.distinctBy {
                    it.lineId?.takeIf { l -> l.isNotBlank() }
                        ?: "${it.schoolId ?: ""}|${(it.sector ?: "").trim().lowercase()}|${it.blockName.trim().lowercase()}"
                }
                // Los PROYECTOS (probando, aún no hecho) tienen sus propias pantallas
                // ("project"/"project:BOULDER"/"project:ROUTE"); el resto de filtros
                // (todos preexistentes) solo muestran lo HECHO, para no mezclarlos.
                val done = all.filter { it.status != "PROJECT" }
                val projects = all.filter { it.status == "PROJECT" }
                val filtered = when {
                    filter == null               -> done
                    filter == "project"           -> projects
                    filter == "project:BOULDER"   -> projects.filter { !it.discipline.equals("ROUTE", true) }
                    filter == "project:ROUTE"     -> projects.filter { it.discipline.equals("ROUTE", true) }
                    // "school:X|sector:Y" (viene de JournalSectorsScreen): primero por
                    // escuela; el filtro por sector se aplica DESPUÉS, una vez resuelto
                    // viaInfo más abajo (el sector no se guarda en la entrada).
                    filter.startsWith("school:") -> {
                        val name = filter.removePrefix("school:").substringBefore("|sector:")
                        done.filter { it.schoolName?.equals(name, ignoreCase = true) == true }
                    }
                    filter == "grade-max" -> {
                        val max = if (uid == null) getMyJournalStats().maxGrade
                                  else getUserStats(uid).maxGrade
                        if (max != null) done.filter { it.grade == max } else emptyList()
                    }
                    // Modalidad: BOULDER (bloque) o ROUTE (vía). Entradas viejas sin
                    // modalidad cuentan como BOULDER (default), igual que en stats.
                    filter == "discipline:BOULDER" -> done.filter { !it.discipline.equals("ROUTE", true) }
                    filter == "discipline:ROUTE"   -> done.filter { it.discipline.equals("ROUTE", true) }
                    else -> done
                }
                // Resuelvo nº de piedra + sector en vivo del catálogo (no se guardan
                // en la entrada). Si falla la red, queda vacío y no se muestran.
                val viaInfoAll = runCatching { getJournalViaInfo(filtered) }.getOrDefault(emptyMap())
                // Segunda pasada: dentro de la escuela, quedarnos solo con las del
                // sector pedido (comparación por nombre de sector, insensible a mayúsculas).
                val entries = if (sectorName != null) {
                    filtered.filter { viaInfoAll[it.id]?.sector?.equals(sectorName, ignoreCase = true) == true }
                } else filtered
                val viaInfo = if (sectorName != null) viaInfoAll.filterKeys { k -> entries.any { it.id == k } } else viaInfoAll
                JournalEntriesUiState.Success(entries, filter, isMine, viaInfo)
            } catch (t: Throwable) {
                JournalEntriesUiState.Error(t.toUserMessage())
            }
        }
    }

    /** «Editar ascenso»: guarda fecha y estilo (solo lo que cambió, por los mismos casos de uso) y recarga una vez. */
    fun editAscent(entry: JournalSession, newDate: String, style: AscentStyle) {
        viewModelScope.launch {
            runCatching {
                if (newDate != entry.date.take(10)) updateJournalDate(entry.id, newDate)
                if (style != AscentStyle.fromFlags(entry.aVista, entry.alFlash)) {
                    updateJournalStyle(entry.id, style.aVista, style.alFlash)
                }
            }.onFailure { e ->
                android.util.Log.e("CumbreEstilo", "editar ascenso ${entry.id} fallo", e)
                _styleError.value = e.toUserMessage()
            }
            load()
        }
    }

    /** C3: cambiar la fecha de una entrada y recargar. */
    fun changeDate(id: String, newDate: String) {
        viewModelScope.launch {
            runCatching { updateJournalDate(id, newDate) }
                .onSuccess { load() }
        }
    }

    /** Cambiar el estilo (a vista / al flash) de una entrada. Independientes. */
    fun changeStyle(id: String, aVista: Boolean, alFlash: Boolean) {
        viewModelScope.launch {
            runCatching { updateJournalStyle(id, aVista, alFlash) }
                .onSuccess { load() }
                .onFailure { e ->
                    android.util.Log.e("CumbreEstilo", "PATCH journal/$id/style fallo", e)
                    _styleError.value = e.toUserMessage()
                }
        }
    }

    fun delete(id: String) {
        if (!isMine) return
        viewModelScope.launch {
            runCatching { deleteJournalEntry(id) }
            load()
        }
    }
}
