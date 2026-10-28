package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonSchoolRow
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.border

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/**
 * Pantalla intermedia entre "Escuelas" y el listado plano del diario: al pulsar
 * una escuela (La Pedriza, etc.) esta pantalla agrupa sus bloques/vías por
 * SECTOR. Las entradas sin sector (o con un sector aún no catalogado en la app)
 * se muestran sueltas, directamente pulsables, sin meterlas en una carpeta
 * "sin sector" — así no se pierden ni obligan a un paso extra.
 */
/** Lo que enseña la tarjeta de un sector del diario (espejo de JournalSectorSummary de iOS). */
data class JournalSectorSummary(val name: String, val count: Int, val maxGrade: String?, val photo: String?, val linePath: String?)

sealed interface JournalSectorsUiState {
    data object Loading : JournalSectorsUiState
    data class Success(
        // Sectores con ≥1 entrada, orden alfabético; cada uno con su recuento, grado más duro y una foto.
        val sectors: List<JournalSectorSummary>,
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
                val calc = com.meteomontana.android.domain.usecase.journal.JournalStatsCalculator
                val sectors = bySector.entries.map { (name, list) ->
                    val gradeOf = { e: JournalSession -> viaInfo[e.id]?.grade ?: e.grade }
                    val sorted = list.sortedByDescending { calc.gradeRank(gradeOf(it) ?: "") }
                    val withPhoto = sorted.firstOrNull { !viaInfo[it.id]?.photoUrl.isNullOrBlank() }
                    JournalSectorSummary(name, list.size, sorted.firstOrNull()?.let(gradeOf),
                        withPhoto?.let { viaInfo[it.id]?.photoUrl }, withPhoto?.let { viaInfo[it.id]?.linePath })
                }.sortedBy { it.name.lowercase() }
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
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.journal_sectors_screen_v2_volver),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(viewModel.schoolName, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            JournalSectorsUiState.Loading -> SkeletonList(count = 8) { SkeletonSchoolRow() }
            is JournalSectorsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is JournalSectorsUiState.Success -> {
                if (s.sectors.isEmpty() && s.loose.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(stringResource(R.string.journal_sectors_screen_v2_sin_entradas_en_esta_escuela), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val top = (s.sectors.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                    LazyColumn(modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.sectors, key = { it.name }) { sec ->
                            SectorRow(sec, top, onClick = { onSectorClick(viewModel.schoolName, sec.name) })
                        }
                        if (s.loose.isNotEmpty()) item {
                            Text(stringResource(R.string.journal_no_sector), style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
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

/** Un sector del diario como tarjeta: foto de una de sus piedras, nombre, cuántos, barra y grado más duro. */
@Composable
private fun SectorRow(sec: JournalSectorSummary, top: Int, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (sec.photo != null) {
            com.meteomontana.android.ui.screens.schools.MiniTopoThumbnail(sec.photo, sec.linePath, sec.maxGrade,
                Modifier.size(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp)))
        } else {
            Box(Modifier.size(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                .background(com.meteomontana.android.ui.theme.Terra.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = com.meteomontana.android.ui.theme.Terra, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(sec.name, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2,
                color = MaterialTheme.colorScheme.onBackground)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(sec.count.toFloat() / top).height(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape).background(com.meteomontana.android.ui.theme.Terra))
                }
                val countText = if (sec.count == 1) stringResource(R.string.journal_sectors_screen_v3_1_bloque) else "${sec.count} bloques"
                Text(countText, fontFamily = com.meteomontana.android.ui.theme.Mono,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 10.sp,
                    color = com.meteomontana.android.ui.theme.Terra)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sec.maxGrade?.takeIf { it.isNotBlank() }?.let { com.meteomontana.android.ui.screens.detail.GradePill(it) }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
