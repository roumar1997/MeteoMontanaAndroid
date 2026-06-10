package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
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
        val isMine: Boolean
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
    private val deleteJournalEntry: DeleteJournalEntryUseCase
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
        else                         -> "Diario"
    }

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                val all = if (uid == null) getMyJournal() else getUserJournal(uid)
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
                    else -> all
                }
                JournalEntriesUiState.Success(filtered, filter, isMine)
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
    viewModel: JournalEntriesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null,
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
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Vacío", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn {
                        items(s.entries, key = { it.id }) { e ->
                            EntryRow(e, canDelete = s.isMine) { viewModel.delete(e.id) }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: JournalSession, canDelete: Boolean = true, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val eGrade = e.grade
                if (!eGrade.isNullOrBlank()) {
                    Box(modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(eGrade,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(e.blockName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text(
                buildString {
                    e.schoolName?.let { append(it) }
                    e.sector?.let {
                        if (this.isNotEmpty()) append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val eNotes = e.notes
            if (!eNotes.isNullOrBlank()) {
                Text(eNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
