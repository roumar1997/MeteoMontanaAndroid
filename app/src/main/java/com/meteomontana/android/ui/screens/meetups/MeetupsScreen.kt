package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

@Composable
fun MeetupsScreen(
    onMeetupClick: (String) -> Unit = {},
    onCreateMeetup: () -> Unit = {},
    viewModel: MeetupsViewModel = hiltViewModel()
) {
    val state by viewModel.listState.collectAsState()
    val alertState by viewModel.alertState.collectAsState()
    val schoolResults by viewModel.schoolResults.collectAsState()
    var showSchoolFilter by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadAlertState() }

    // Filtro de privacidad aplicado localmente (los datos ya están cargados)
    val displayedMeetups = remember(state.meetups, state.filterPrivacy) {
        if (state.filterPrivacy == null) state.meetups
        else state.meetups.filter { it.privacy == state.filterPrivacy }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(0.dp))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "QUEDADAS",
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (displayedMeetups.isEmpty()) "Quedar a escalar"
                           else "${displayedMeetups.size} activas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.loadMeetups() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                }
                val alertEnabled = alertState?.enabled == true
                IconButton(onClick = { viewModel.toggleAlert(!alertEnabled) }) {
                    Icon(
                        if (alertEnabled) Icons.Outlined.NotificationsActive
                        else Icons.Outlined.NotificationsOff,
                        contentDescription = if (alertEnabled) "Alerta activa" else "Activar alerta",
                        tint = if (alertEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCreateMeetup) {
                    Icon(Icons.Outlined.Add, contentDescription = "Crear quedada",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ── Filtros ──
        MeetupsFilterBar(
            selectedRelation = state.filterRelation,
            selectedPrivacy = state.filterPrivacy,
            filterSchoolName = state.filterSchoolName,
            onRelationSelected = { viewModel.setFilterRelation(it) },
            onPrivacySelected = { viewModel.setFilterPrivacy(it) },
            onSchoolFilterTap = { showSchoolFilter = true },
            onClearSchool = { viewModel.setFilterSchool(null, null) }
        )

        HorizontalDivider()

        // ── Contenido ──
        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading && displayedMeetups.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.error != null && displayedMeetups.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No se pudo cargar", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.sm))
                        TextButton(onClick = { viewModel.loadMeetups() }) {
                            Text("REINTENTAR")
                        }
                    }
                }
                displayedMeetups.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.Groups, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.md))
                        Text("Sin quedadas activas",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.sm))
                        Text("Crea una para quedar a escalar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                        items(displayedMeetups, key = { it.id }) { meetup ->
                            MeetupListItem(meetup = meetup, onClick = { onMeetupClick(meetup.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // Picker de escuela para el filtro
    if (showSchoolFilter) {
        MeetupSchoolFilterDialog(
            results = schoolResults,
            onQueryChange = { viewModel.searchSchools(it) },
            onSelect = { school ->
                viewModel.setFilterSchool(school.id, school.name)
                showSchoolFilter = false
                viewModel.clearSchoolSearch()
            },
            onDismiss = { showSchoolFilter = false; viewModel.clearSchoolSearch() }
        )
    }
}

@Composable
private fun MeetupsFilterBar(
    selectedRelation: String?,
    selectedPrivacy: String?,
    filterSchoolName: String?,
    onRelationSelected: (String?) -> Unit,
    onPrivacySelected: (String?) -> Unit,
    onSchoolFilterTap: () -> Unit,
    onClearSchool: () -> Unit
) {
    Column {
        // Fila 1: relación + privacidad
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            FilterChip(label = "TODAS", selected = selectedRelation == null && selectedPrivacy == null,
                onClick = { onRelationSelected(null); onPrivacySelected(null) })
            FilterChip(label = "SIGUIENDO", selected = selectedRelation == "following",
                onClick = { onRelationSelected(if (selectedRelation == "following") null else "following") })
            FilterChip(label = "SOLO MUJERES", selected = selectedPrivacy == "WOMEN",
                onClick = { onPrivacySelected(if (selectedPrivacy == "WOMEN") null else "WOMEN") })
            FilterChip(label = "SEGUIDORES", selected = selectedPrivacy == "FOLLOWERS",
                onClick = { onPrivacySelected(if (selectedPrivacy == "FOLLOWERS") null else "FOLLOWERS") })
        }
        // Fila 2: filtro de escuela
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onSchoolFilterTap)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = filterSchoolName ?: "Filtrar por escuela…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (filterSchoolName != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (filterSchoolName != null) {
                IconButton(onClick = onClearSchool, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Quitar filtro escuela",
                        modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
             else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MeetupListItem(meetup: Meetup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Foto o avatar creador
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val photo = meetup.photoUrl ?: meetup.creatorPhotoUrl
            if (photo != null) {
                AsyncImage(
                    model = photo, contentDescription = meetup.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Outlined.Groups, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Nombre de la escuela
            meetup.schoolName?.let {
                Text(it.uppercase(), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(meetup.name, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fechas (primera y última)
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val datesText = if (meetup.days.size == 1) meetup.days.first()
                                else "${meetup.days.first()} – ${meetup.days.last()}"
                Text(datesText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Privacidad
                if (meetup.privacy != "OPEN") {
                    Icon(Icons.Outlined.Lock, contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(privacyLabel(meetup.privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Miembros
                Icon(Icons.Outlined.Person, contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val limitText = meetup.memberLimit?.let { "${meetup.memberCount}/$it" }
                    ?: "${meetup.memberCount}"
                Text(limitText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Disciplina
                meetup.discipline?.let {
                    Text("· ${disciplineLabel(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Badge "UNIDO" o "LLENO"
        when {
            meetup.joined -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    Text("UNIDO", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold)
                }
            }
            meetup.isFull -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    Text("LLENO", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MeetupSchoolFilterDialog(
    results: List<com.meteomontana.android.domain.model.School>,
    onQueryChange: (String) -> Unit,
    onSelect: (com.meteomontana.android.domain.model.School) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar escuela", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onQueryChange(it) },
                    placeholder = { Text("Ej. Zarzalejo, Pedriza…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp)
                )
                if (results.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(results) { school ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(school) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(school.name, style = MaterialTheme.typography.bodyMedium)
                                school.location?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                } else if (query.length >= 2) {
                    Text("Sin resultados", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

internal fun privacyLabel(privacy: String) = when (privacy) {
    "FOLLOWERS" -> "Solo seguidores"
    "WOMEN"     -> "No mixto"
    else        -> "Abierta"
}

internal fun disciplineLabel(discipline: String) = when (discipline) {
    "BOULDER" -> "Bloque"
    "ROUTE"   -> "Vía"
    "BOTH"    -> "Bloque + Vía"
    else      -> discipline
}

