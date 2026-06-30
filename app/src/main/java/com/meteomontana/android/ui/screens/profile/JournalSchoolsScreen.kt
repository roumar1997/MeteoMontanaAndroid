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
    val state by viewModel.state.collectAsState()

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
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.schools, key = { it.schoolName }) { school ->
                            SchoolRow(school, onClick = { onSchoolClick(school.schoolName) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolRow(school: SchoolStats, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(school.schoolName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            val blockCountText = if (school.blockCount == 1)
                stringResource(R.string.journal_schools_block_count_one, school.blockCount)
            else
                stringResource(R.string.journal_schools_block_count_other, school.blockCount)
            Text(blockCountText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(school.maxGrade ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
    }
}
