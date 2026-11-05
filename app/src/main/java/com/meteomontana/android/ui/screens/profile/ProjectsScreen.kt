package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonGradeRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.usecase.journal.GetJournalViaInfoUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserJournalUseCase
import com.meteomontana.android.domain.usecase.journal.ViaCatalogInfo
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Proyectos": bloques/vías que estás probando pero aún no te han salido.
 * Una sola pantalla (Álvaro, 2026-10-28, espejo de ProjectsView.swift): selector «Bloques N | Vías N» y la lista
 * justo debajo. Antes eran dos cuadrados que llevaban a otra pantalla con un segundo botón de volver.
 */
sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data class Success(
        val entries: List<JournalSession>,
        val viaInfo: Map<String, ViaCatalogInfo>
    ) : ProjectsUiState {
        private fun isRoute(e: JournalSession) = e.discipline.equals("ROUTE", true)
        val routeCount: Int get() = entries.count(::isRoute)
        val boulderCount: Int get() = entries.size - routeCount
        fun of(discipline: String) = entries.filter { isRoute(it) == (discipline == ROUTE) }
    }
    data class Error(val message: String) : ProjectsUiState

    companion object {
        const val BOULDER = "BOULDER"
        const val ROUTE = "ROUTE"
    }
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMyJournal: GetMyJournalUseCase,
    private val getUserJournal: GetUserJournalUseCase,
    private val getJournalViaInfo: GetJournalViaInfoUseCase
) : ViewModel() {
    private val uid: String? = savedStateHandle.get<String>("uid")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                val all = if (uid == null) getMyJournal() else getUserJournal(uid)
                val projects = all.filter { it.status == "PROJECT" }
                // Piedra, sector, grado y miniatura de cada proyecto, del catálogo (si falla, la lista sale sin ellos).
                val viaInfo = runCatching { getJournalViaInfo(projects) }.getOrDefault(emptyMap())
                ProjectsUiState.Success(projects, viaInfo)
            } catch (t: Throwable) {
                ProjectsUiState.Error(t.toUserMessage())
            }
        }
    }
}

@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onOpenSchool: (schoolId: String, via: String?, viaId: String?) -> Unit = { _, _, _ -> },
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = LocalContext.current.getSharedPreferences("projects", android.content.Context.MODE_PRIVATE)
    // La última pestaña usada; si esa está vacía y la otra no, se abre la otra.
    var discipline by remember { mutableStateOf(prefs.getString("discipline", ProjectsUiState.BOULDER) ?: ProjectsUiState.BOULDER) }
    (state as? ProjectsUiState.Success)?.let { s ->
        LaunchedEffect(s) {
            val count = if (discipline == ProjectsUiState.ROUTE) s.routeCount else s.boulderCount
            if (count == 0) discipline = if (s.boulderCount > 0) ProjectsUiState.BOULDER else ProjectsUiState.ROUTE
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(stringResource(R.string.projects_screen_v2_proyectos), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            ProjectsUiState.Loading -> SkeletonList(count = 6) { SkeletonGradeRow() }
            is ProjectsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is ProjectsUiState.Success -> {
                if (s.entries.isEmpty()) {
                    ProjectsEmpty(
                        title = stringResource(R.string.projects_screen_sin_proyectos_todavia),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        ProjectsSwitcher(s.boulderCount, s.routeCount, discipline) {
                            discipline = it
                            prefs.edit().putString("discipline", it).apply()
                        }
                        val shown = s.of(discipline)
                        if (shown.isEmpty()) {
                            ProjectsEmpty(
                                title = stringResource(
                                    if (discipline == ProjectsUiState.ROUTE) R.string.projects_empty_routes
                                    else R.string.projects_empty_boulders
                                ),
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                            )
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(shown, key = { it.id }) { e ->
                                    EntryRow(
                                        e, canDelete = false,
                                        info = s.viaInfo[e.id],
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
    }
}

/** «Bloques N | Vías N»: el mismo selector de píldora que Escuelas / Vías-Bloques. */
@Composable
private fun ProjectsSwitcher(boulders: Int, routes: Int, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), com.meteomontana.android.ui.theme.CumbrePillShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ProjectsTab(stringResource(R.string.projects_tab_boulders), boulders, selected == ProjectsUiState.BOULDER,
            Modifier.weight(1f)) { onSelect(ProjectsUiState.BOULDER) }
        ProjectsTab(stringResource(R.string.projects_tab_routes), routes, selected == ProjectsUiState.ROUTE,
            Modifier.weight(1f)) { onSelect(ProjectsUiState.ROUTE) }
    }
}

@Composable
private fun ProjectsTab(title: String, count: Int, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .background(if (active) com.meteomontana.android.ui.theme.Terra else androidx.compose.ui.graphics.Color.Transparent,
                com.meteomontana.android.ui.theme.CumbrePillShape)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        Text(title, style = com.meteomontana.android.ui.theme.EyebrowTextStyle.copy(fontSize = 12.sp), color = color)
        Text(count.toString(), style = com.meteomontana.android.ui.theme.EyebrowTextStyle.copy(fontSize = 12.sp),
            color = color.copy(alpha = 0.7f))
    }
}

@Composable
private fun ProjectsEmpty(title: String, modifier: Modifier) {
    Box(modifier, Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Flag, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 10.dp))
            Text(stringResource(R.string.projects_screen_marca_la_p_de),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
