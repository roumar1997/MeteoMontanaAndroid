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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
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
 * "Proyectos": bloques/vías que estás probando pero aún no te han salido.
 * Igual que Vías/Bloques hechos, con su propio split BLOQUES/VÍAS.
 */
sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data class Success(val boulderCount: Int, val routeCount: Int) : ProjectsUiState
    data class Error(val message: String) : ProjectsUiState
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMyJournal: GetMyJournalUseCase,
    private val getUserJournal: GetUserJournalUseCase
) : ViewModel() {
    private val uid: String? = savedStateHandle.get<String>("uid")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                val all = if (uid == null) getMyJournal() else getUserJournal(uid)
                val projects = all.filter { it.status == "PROJECT" }
                val routeCount = projects.count { it.discipline.equals("ROUTE", true) }
                ProjectsUiState.Success(projects.size - routeCount, routeCount)
            } catch (t: Throwable) {
                ProjectsUiState.Error(t.toUserMessage())
            }
        }
    }
}

@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onOpenBoulders: () -> Unit,
    onOpenRoutes: () -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Proyectos", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            ProjectsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is ProjectsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is ProjectsUiState.Success -> {
                if (s.boulderCount == 0 && s.routeCount == 0) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⛏", style = MaterialTheme.typography.displayMedium)
                            Text("Sin proyectos todavía",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text("Marca ⛏ en una vía dentro de su piedra para probarla como proyecto.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProjectStatCell("BLOQUES", s.boulderCount, Modifier.weight(1f), onOpenBoulders)
                        ProjectStatCell("VÍAS", s.routeCount, Modifier.weight(1f), onOpenRoutes)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectStatCell(label: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
