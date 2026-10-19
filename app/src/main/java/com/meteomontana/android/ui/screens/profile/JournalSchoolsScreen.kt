package com.meteomontana.android.ui.screens.profile

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import com.meteomontana.android.domain.model.SchoolStats
import com.meteomontana.android.domain.usecase.journal.GetMyJournalStatsUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserStatsUseCase
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface JournalSchoolsUiState {
    data object Loading : JournalSchoolsUiState
    data class Success(val schools: List<SchoolStats>) : JournalSchoolsUiState
    data class Error(val message: String) : JournalSchoolsUiState
}

@HiltViewModel
class JournalSchoolsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMyStats: GetMyJournalStatsUseCase,
    private val getUserStats: GetUserStatsUseCase
) : ViewModel() {
    private val uid: String? = savedStateHandle.get<String>("uid")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow<JournalSchoolsUiState>(JournalSchoolsUiState.Loading)
    val state: StateFlow<JournalSchoolsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                val stats = if (uid == null) getMyStats() else getUserStats(uid)
                JournalSchoolsUiState.Success(stats.bySchool)
            } catch (t: Throwable) {
                JournalSchoolsUiState.Error(t.toUserMessage())
            }
        }
    }
}

@Composable
fun JournalSchoolsScreen(
    onBack: () -> Unit,
    onSchoolClick: (String) -> Unit,
    viewModel: JournalSchoolsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(stringResource(R.string.schools_title), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            JournalSchoolsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is JournalSchoolsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is JournalSchoolsUiState.Success -> {
                if (s.schools.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(stringResource(R.string.journal_schools_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val top = (s.schools.maxOfOrNull { it.blockCount } ?: 1).coerceAtLeast(1)
                    LazyColumn(modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.schools, key = { it.schoolName }) { school ->
                            SchoolRow(school, top, onClick = { onSchoolClick(school.schoolName) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Una escuela del diario como tarjeta (igual que «Tus escuelas» de Estadísticas): ascensos en grande, nombre, barra
 * proporcional a la escuela con más y el grado máximo en su color (Álvaro, 2026-10-19).
 */
@Composable
private fun SchoolRow(school: SchoolStats, top: Int, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Row(modifier = Modifier.fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
        .clickable(onClick = onClick)
        .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(school.blockCount.toString(), fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 26.sp,
                color = com.meteomontana.android.ui.theme.Terra)
            Text(stringResource(R.string.stats_ascents_word), fontFamily = com.meteomontana.android.ui.theme.Mono,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(school.schoolName, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 15.sp,
                maxLines = 2, color = MaterialTheme.colorScheme.onBackground)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(school.blockCount.toFloat() / top).height(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape).background(com.meteomontana.android.ui.theme.Terra))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            school.maxGrade?.takeIf { it.isNotBlank() }?.let { com.meteomontana.android.ui.screens.detail.GradePill(it) }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
        }
    }
}
