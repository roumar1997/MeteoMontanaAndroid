package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.usecase.journal.GetJournalViaInfoUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserJournalUseCase
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pantalla intermedia entre "Escuelas" y el listado plano del diario: al pulsar
 * una escuela (La Pedriza, etc.) esta pantalla agrupa sus bloques/vías por
 * SECTOR. Las entradas sin sector (o con un sector aún no catalogado en la app)
 * se muestran sueltas, directamente pulsables, sin meterlas en una carpeta
 * "sin sector" — así no se pierden ni obligan a un paso extra.
 */
sealed interface JournalSectorsUiState {
    data object Loading : JournalSectorsUiState
    data class Success(
        // Sectores con ≥1 entrada, orden alfabético; cada uno con su recuento.
        val sectors: List<Pair<String, Int>>,
        // Entradas sin sector resuelto: se muestran directamente en esta pantalla.
        val loose: List<JournalSession>,
        val looseInfo: Map<String, com.meteomontana.android.domain.usecase.journal.ViaCatalogInfo>
    ) : JournalSectorsUiState
    data class Error(val message: String) : JournalSectorsUiState
}

@HiltViewModel
class JournalSectorsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMyJournal: GetMyJournalUseCase,
    private val getUserJournal: GetUserJournalUseCase,
    private val getJournalViaInfo: GetJournalViaInfoUseCase,
    private val outboxRepo: com.meteomontana.android.data.outbox.OutboxRepository
) : ViewModel() {
    val schoolName: String = savedStateHandle.get<String>("school").orEmpty()
    private val uid: String? = savedStateHandle.get<String>("uid")?.takeIf { it.isNotBlank() }
    private val isMine: Boolean = uid == null

    private val _state = MutableStateFlow<JournalSectorsUiState>(JournalSectorsUiState.Loading)
    val state: StateFlow<JournalSectorsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                val server = if (uid == null) getMyJournal() else getUserJournal(uid)
                val visible = if (isMine) {
                    val pendingDeletes = outboxRepo.all()
                        .filter { it.type == com.meteomontana.android.data.outbox.OutboxType.JOURNAL_DELETE }
                        .map { it.payloadJson }.toSet()
                    if (pendingDeletes.isEmpty()) server
                    else server.filter {
                        "${it.schoolId ?: ""}|${it.blockName.trim().lowercase()}" !in pendingDeletes
                    }
                } else server
                val all = visible.distinctBy {
                    "${it.schoolId ?: ""}|${(it.sector ?: "").trim().lowercase()}|${it.blockName.trim().lowercase()}"
                }
                val entries = all.filter { it.schoolName?.equals(schoolName, ignoreCase = true) == true }
                val viaInfo = runCatching { getJournalViaInfo(entries) }.getOrDefault(emptyMap())

                val bySector = entries.filter { !viaInfo[it.id]?.sector.isNullOrBlank() }
                    .groupBy { viaInfo[it.id]!!.sector!! }
                val sectors = bySector.entries
                    .map { it.key to it.value.size }
                    .sortedBy { it.first.lowercase() }
                val loose = entries.filter { viaInfo[it.id]?.sector.isNullOrBlank() }

                JournalSectorsUiState.Success(sectors, loose, viaInfo)
            } catch (t: Throwable) {
                JournalSectorsUiState.Error(t.toUserMessage())
            }
        }
    }
}

@Composable
fun JournalSectorsScreen(
    onBack: () -> Unit,
    onSectorClick: (schoolName: String, sectorName: String) -> Unit,
    onOpenSchool: (schoolId: String, via: String?, viaId: String?) -> Unit,
    viewModel: JournalSectorsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(viewModel.schoolName, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            JournalSectorsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is JournalSectorsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is JournalSectorsUiState.Success -> {
                if (s.sectors.isEmpty() && s.loose.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Sin entradas en esta escuela", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.sectors, key = { it.first }) { (sectorName, count) ->
                            SectorRow(sectorName, count, onClick = { onSectorClick(viewModel.schoolName, sectorName) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                        items(s.loose, key = { it.id }) { e ->
                            EntryRow(
                                e, canDelete = false,
                                info = s.looseInfo[e.id],
                                onClick = { e.schoolId?.let { onOpenSchool(it, e.blockName, e.lineId) } },
                                onDelete = {}
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
private fun SectorRow(name: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Folder, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column {
                Text(name, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                val countText = if (count == 1) "1 bloque" else "$count bloques"
                Text(countText, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
