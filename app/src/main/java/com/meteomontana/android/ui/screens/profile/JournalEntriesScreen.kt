package com.meteomontana.android.ui.screens.profile

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
    private val getJournalViaInfo: com.meteomontana.android.domain.usecase.journal.GetJournalViaInfoUseCase,
    private val outboxRepo: com.meteomontana.android.data.outbox.OutboxRepository
) : ViewModel() {
    private val filter: String? = savedStateHandle["filter"]
    private val uid: String? = savedStateHandle.get<String>("uid")?.takeIf { it.isNotBlank() }
    private val isMine: Boolean = uid == null

    private val _state = MutableStateFlow<JournalEntriesUiState>(JournalEntriesUiState.Loading)
    val state: StateFlow<JournalEntriesUiState> = _state.asStateFlow()

    // Filtro compuesto "school:X|sector:Y" (llegando desde JournalSectorsScreen):
    // el título es el nombre del sector, no de la escuela (ya viniste de ahí).
    private val sectorName: String? = filter
        ?.takeIf { it.contains("|sector:") }
        ?.substringAfter("|sector:")

    val title: String = when {
        filter == null               -> if (isMine) "Todos mis bloques" else "Todos los bloques"
        sectorName != null           -> sectorName
        filter.startsWith("school:") -> filter.removePrefix("school:")
        filter == "grade-max"        -> "Grado máximo"
        filter == "discipline:BOULDER" -> if (isMine) "Mis bloques" else "Bloques"
        filter == "discipline:ROUTE"   -> if (isMine) "Mis vías" else "Vías"
        filter == "project"          -> if (isMine) "Mis proyectos" else "Proyectos"
        filter == "project:BOULDER"  -> if (isMine) "Mis proyectos · bloques" else "Proyectos · bloques"
        filter == "project:ROUTE"    -> if (isMine) "Mis proyectos · vías" else "Proyectos · vías"
        else                         -> "Diario"
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

    /** C3: cambiar la fecha de una entrada y recargar. */
    fun changeDate(id: String, newDate: String) {
        viewModelScope.launch {
            runCatching { updateJournalDate(id, newDate) }
                .onSuccess { load() }
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

@Composable
fun JournalEntriesScreen(
    onBack: () -> Unit,
    onOpenSchool: (schoolId: String, via: String?, viaId: String?) -> Unit = { _, _, _ -> },
    viewModel: JournalEntriesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // G: filtro por grado activo (null = todos).
    var gradeFilter by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(viewModel.title, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            JournalEntriesUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is JournalEntriesUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is JournalEntriesUiState.Success -> {
                if (s.entries.isEmpty()) {
                    com.meteomontana.android.ui.components.EmptyState(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "Tu diario está vacío",
                        message = "Marca el ✓ de una vía dentro de su piedra (en el detalle de una escuela) y aparecerá aquí, con su grado y sector."
                    )
                } else {
                    LazyColumn {
                        item {
                            com.meteomontana.android.ui.components.FirstTimeHint(
                                hintKey = "journal_tap_via",
                                text = "Toca una vía para ir directamente a su piedra en la escuela."
                            )
                        }
                        // Si estamos viendo una escuela concreta, fila para abrir
                        // la escuela (sin piedra).
                        val headerSchoolId = s.entries.firstOrNull { !it.schoolId.isNullOrBlank() }?.schoolId
                        if (s.filter?.startsWith("school:") == true && headerSchoolId != null) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onOpenSchool(headerSchoolId, null, null) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.common_view_school),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        // G: filtro por GRADO (chips con la paleta de topos).
                        val grades = s.entries
                            .mapNotNull { it.grade?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
                            .distinct()
                            .sortedByDescending {
                                com.meteomontana.android.domain.usecase.journal.JournalStatsCalculator.gradeRank(it)
                            }
                        if (grades.size > 1) {
                            item(key = "grade-filter") {
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    items(grades.size) { gi ->
                                        val g = grades[gi]
                                        val gs = com.meteomontana.android.ui.theme.gradeStyle(g)
                                        val accent = if (gs.dark) MaterialTheme.colorScheme.onSurface else gs.stroke
                                        val active = gradeFilter == g
                                        Box(Modifier
                                            .background(
                                                if (active) accent else MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(6.dp))
                                            .border(1.dp, accent, RoundedCornerShape(6.dp))
                                            .clickable { gradeFilter = if (active) null else g }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)) {
                                            Text(g, style = MaterialTheme.typography.labelMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = if (active) androidx.compose.ui.graphics.Color.White
                                                        else accent)
                                        }
                                    }
                                }
                            }
                        }
                        val visibleEntries = gradeFilter?.let { g ->
                            s.entries.filter { it.grade?.trim()?.equals(g, ignoreCase = true) == true }
                        } ?: s.entries
                        // C3: agrupado por MES (cabecera "JULIO 2026 - N"), orden
                        // cronologico descendente por fecha de la sesion.
                        val byMonth = visibleEntries.sortedByDescending { it.date }
                            .groupBy { it.date.take(7) }
                        byMonth.forEach { (month, monthEntries) ->
                            item(key = "month-" + month) {
                                Text(
                                    formatMonthHeader(month) + " \u00b7 " + monthEntries.size,
                                    style = EyebrowTextStyle,
                                    color = com.meteomontana.android.ui.theme.Terra,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(monthEntries, key = { it.id }) { e ->
                                EntryRow(
                                    e, canDelete = s.isMine,
                                    info = s.viaInfo[e.id],
                                    onClick = { e.schoolId?.let { onOpenSchool(it, e.blockName, e.lineId) } },
                                    onDelete = { viewModel.delete(e.id) },
                                    onChangeDate = if (s.isMine) ({ newDate ->
                                        viewModel.changeDate(e.id, newDate)
                                    }) else null
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}

// internal (no private): reutilizado por JournalSectorsScreen.kt (mismo paquete)
// para las entradas sin sector, que se muestran directamente sin subcarpeta.
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun EntryRow(
    e: JournalSession,
    canDelete: Boolean = true,
    info: com.meteomontana.android.domain.usecase.journal.ViaCatalogInfo? = null,
    onClick: () -> Unit = {},
    onDelete: () -> Unit,
    /** C3: cambiar la fecha de la entrada (null = no editable, diario ajeno). */
    onChangeDate: ((String) -> Unit)? = null
) {
    var showDatePicker by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    if (showDatePicker && onChangeDate != null) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= System.currentTimeMillis()
            })
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onChangeDate(java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) { androidx.compose.material3.DatePicker(state = pickerState) }
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (e.schoolId != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Grado ACTUAL del catálogo (refleja correcciones) o, si no se
                // pudo resolver, el guardado al marcar la vía.
                val eGrade = info?.grade ?: e.grade
                if (!eGrade.isNullOrBlank()) {
                    val gs = com.meteomontana.android.ui.theme.gradeStyle(eGrade)
                    Box(modifier = Modifier
                        .background(gs.stroke, RoundedCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(eGrade,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (gs.dark) androidx.compose.ui.graphics.Color.Black
                                    else androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
            val deleted = info?.deleted == true
            Text(e.blockName,
                style = MaterialTheme.typography.titleMedium,
                color = if (deleted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onBackground)
            if (deleted) {
                Text("VÍA ELIMINADA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Escuela + (nº de piedra · sector) resueltos del catálogo en vivo.
            // Si no se pudo resolver (sin red / vía no catalogada) solo va la escuela.
            val subtitle = buildString {
                append(e.schoolName.orEmpty())
                info?.boulderNumber?.let { if (isNotEmpty()) append(" · "); append("Piedra $it") }
                info?.sector?.let { if (isNotEmpty()) append(" · "); append(it) }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val eNotes = e.notes
            // Ocultamos la nota auto "Piedra: N" (obsoleta: el número se recicla).
            if (!eNotes.isNullOrBlank() && !eNotes.startsWith("Piedra: ")) {
                Text(eNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        // Fecha a la DERECHA (como iOS — feedback R2), pulsable si es editable.
        Text(e.date,
            style = MaterialTheme.typography.labelMedium,
            color = if (onChangeDate != null) com.meteomontana.android.ui.theme.Terra
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = (if (onChangeDate != null)
                Modifier.clickable { showDatePicker = true } else Modifier)
                .padding(horizontal = 4.dp))
        // Flecha que indica que la fila es pulsable → abre la piedra
        if (e.schoolId != null) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Ver piedra",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}


private val MONTH_NAMES = listOf(
    "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
    "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE")

internal fun formatMonthHeader(yyyyMm: String): String = runCatching {
    MONTH_NAMES[yyyyMm.substringAfter('-').toInt() - 1] + " " + yyyyMm.take(4)
}.getOrDefault(yyyyMm)
