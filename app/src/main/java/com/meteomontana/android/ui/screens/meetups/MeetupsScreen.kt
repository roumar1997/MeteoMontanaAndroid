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
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    LaunchedEffect(Unit) { viewModel.loadAlertState() }

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
                    text = if (state.meetups.isEmpty()) "Quedar a escalar" else "${state.meetups.size} activas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.loadMeetups() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                }
                // Botón alerta: campana activa (terra) o apagada
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

        // ── Filtros de relación ──
        MeetupsFilterBar(
            selected = state.filterRelation,
            onSelected = { viewModel.setFilterRelation(it) }
        )

        HorizontalDivider()

        // ── Contenido ──
        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.meetups.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.error != null && state.meetups.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No se pudo cargar", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.sm))
                        androidx.compose.material3.TextButton(onClick = { viewModel.loadMeetups() }) {
                            Text("REINTENTAR")
                        }
                    }
                }
                state.meetups.isEmpty() -> {
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
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = Spacing.xxl)
                    ) {
                        items(state.meetups, key = { it.id }) { meetup ->
                            MeetupListItem(
                                meetup = meetup,
                                onClick = { onMeetupClick(meetup.id) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetupsFilterBar(
    selected: String?,
    onSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        FilterChip(label = "TODAS", selected = selected == null, onClick = { onSelected(null) })
        FilterChip(label = "SIGUIENDO", selected = selected == "following",
            onClick = { onSelected("following") })
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

