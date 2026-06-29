package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
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
    private val getJournalViaInfo: com.meteomontana.android.domain.usecase.journal.GetJournalViaInfoUseCase,
    private val outboxRepo: com.meteomontana.android.data.outbox.OutboxRepository
) : ViewModel() {
    private val filter: String? = savedStateHandle["filter"]
    private val uid: String? = savedStateHandle.get<String>("uid")?.takeIf { it.isNotBlank() }
    private val isMine: Boolean = uid == null

    private val _state = MutableStateFlow<JournalEntriesUiState>(JournalEntriesUiState.Loading)
    val state: StateFlow<JournalEntriesUiState> = _state.asStateFlow()

    val title: String = when {
        filter == null               -> if (isMine) "Todos mis bloques" else "Todos los bloques"
        filter.startsWith("school:") -> filter.removePrefix("school:")
        filter == "grade-max"        -> "Grado máximo"
        filter == "discipline:BOULDER" -> if (isMine) "Mis bloques" else "Bloques"
        filter == "discipline:ROUTE"   -> if (isMine) "Mis vías" else "Vías"
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
                        "${it.schoolId ?: ""}|${it.blockName.trim().lowercase()}" !in pendingDeletes
                    }
                } else server
                // Sin duplicados: la misma vía marcada varias veces (mismo
                // escuela|sector|vía) se colapsa en una (la más reciente; el
                // servidor las devuelve por fecha desc).
                val all = visible.distinctBy {
                    "${it.schoolId ?: ""}|${(it.sector ?: "").trim().lowercase()}|${it.blockName.trim().lowercase()}"
                }
                val filtered = when {
                    filter == null               -> all
                    filter.startsWith("school:") -> {
                        val name = filter.removePrefix("school:")
                        all.filter { it.schoolName?.equals(name, ignoreCase = true) == true }
                    }
                    filter == "grade-max" -> {
                        val max = if (uid == null) getMyJournalStats().maxGrade
                                  else getUserStats(uid).maxGrade
                        if (max != null) all.filter { it.grade == max } else emptyList()
                    }
                    // Modalidad: BOULDER (bloque) o ROUTE (vía). Entradas viejas sin
                    // modalidad cuentan como BOULDER (default), igual que en stats.
                    filter == "discipline:BOULDER" -> all.filter { !it.discipline.equals("ROUTE", true) }
                    filter == "discipline:ROUTE"   -> all.filter { it.discipline.equals("ROUTE", true) }
                    else -> all
                }
                // Resuelvo nº de piedra + sector en vivo del catálogo (no se guardan
                // en la entrada). Si falla la red, queda vacío y no se muestran.
                val viaInfo = runCatching { getJournalViaInfo(filtered) }.getOrDefault(emptyMap())
                JournalEntriesUiState.Success(filtered, filter, isMine, viaInfo)
            } catch (t: Throwable) {
                JournalEntriesUiState.Error(t.toUserMessage())
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

@Composable
fun JournalEntriesScreen(
    onBack: () -> Unit,
    onOpenSchool: (schoolId: String, via: String?, viaId: String?) -> Unit = { _, _, _ -> },
    viewModel: JournalEntriesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

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
                        items(s.entries, key = { it.id }) { e ->
                            EntryRow(
                                e, canDelete = s.isMine,
                                info = s.viaInfo[e.id],
                                onClick = { e.schoolId?.let { onOpenSchool(it, e.blockName, e.lineId) } },
                                onDelete = { viewModel.delete(e.id) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    e: JournalSession,
    canDelete: Boolean = true,
    info: com.meteomontana.android.domain.usecase.journal.ViaCatalogInfo? = null,
    onClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (e.schoolId != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
