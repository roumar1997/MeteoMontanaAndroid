package com.meteomontana.android.ui.screens.meetups

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonCard
import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.util.AppText
import androidx.compose.animation.AnimatedVisibility
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.meteomontana.android.ui.guide.guideTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.util.Geo
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

// Tarjeta de una quedada en la lista, filtros de la lista y textos de fecha, privacidad y modalidad.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetupListItem(meetup: Meetup, dayScoresMap: Map<String, Int> = emptyMap(),
                   distanceKm: Double? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Foto
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val photo = meetup.photoUrl  // solo la foto de la quedada, no la del creador
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = meetup.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Outlined.Groups, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Info
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Escuela + distancia como eyebrow
            val eyebrow = buildString {
                meetup.schoolName?.let { append(it.uppercase()) }
                if (distanceKm != null) append(" · ${distanceKm.toInt()} KM")
            }
            if (eyebrow.isNotBlank()) {
                Text(eyebrow, style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Nombre en serif
            Text(meetup.name, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                fontFamily = com.meteomontana.android.ui.theme.SourceSerif4Family)
            // Días con score individual
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                meetup.days.forEach { day ->
                    val score = dayScoresMap["${meetup.schoolId}_$day"]
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(formatDayMonth(day), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (score != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(scoreColor(score))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("$score", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // Meta: disciplina + privacidad
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                meetup.discipline?.let {
                    Text(disciplineLabel(it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (meetup.privacy != "OPEN") {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Outlined.Lock, null, Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(privacyLabel(meetup.privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Right: badge + members
        Column(horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                meetup.joined -> {
                    Box(Modifier.clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)) {
                        Text(stringResource(R.string.meetups_joined).uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                meetup.isFull -> {
                    Box(Modifier.clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)) {
                        Text(stringResource(R.string.meetups_full).uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Outlined.Person, null, Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val limitText = meetup.memberLimit?.let { "${meetup.memberCount}/$it" }
                    ?: "${meetup.memberCount}"
                Text(limitText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Envoltorio fino sobre el CumbreChip compartido: antes tenía su propio
// diseño copiado a mano (Box + border sueltos) y por eso divergió del resto
// de la app cuando el radio pasó a píldora en 2026-08-21. El envoltorio
// existe solo para mantener la lambda al final en las 8 llamadas de abajo
// (CumbreChip tiene `modifier` después de `onClick`, así que no admite
// lambda-trailing directamente).
@Composable
internal fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    com.meteomontana.android.ui.components.CumbreChip(label = label, selected = selected, onClick = onClick)
}

@Composable
internal fun FilterGroupLabel(text: String) {
    Text(text, style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun MeetupSchoolFilterDialog(
    results: List<com.meteomontana.android.domain.model.School>,
    onQueryChange: (String) -> Unit,
    onSelect: (com.meteomontana.android.domain.model.School) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meetups_screen_v2_buscar_escuela), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onQueryChange(it) },
                    placeholder = { Text(stringResource(R.string.meetups_screen_v2_ej_zarzalejo_pedriza)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp)
                )
                if (results.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(results, key = { it.id }) { school ->
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
                    Text(stringResource(R.string.meetups_screen_v2_sin_resultados), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

// ── Helpers ──

internal fun monthNames() = CalendarLabels.monthsShort().map { it.lowercase() }

internal fun nextNDaysFilter(n: Int): List<Pair<String, String>> {
    val dayNames = CalendarLabels.daysShortSunFirst().map { it.lowercase() }
    val monthNames = monthNames()
    val result = mutableListOf<Pair<String, String>>()
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L
    for (i in 0 until n) {
        val ts = now + i * dayMs
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        val y = cal.get(java.util.Calendar.YEAR)
        val mo = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val iso = "%04d-%02d-%02d".format(y, mo, d)
        val label = "${dayNames[dow]} $d ${monthNames[mo - 1]}"
        result.add(iso to label)
    }
    return result
}

internal fun formatDayMonth(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val month = parts[1].toIntOrNull() ?: return iso
    val day = parts[2].toIntOrNull() ?: return iso
    return "$day ${monthNames().getOrElse(month - 1) { "?" }}"
}

/** Color de la nota en las quedadas (lista y crear): verde, ámbar, rojo o gris. */
internal fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF22C55E)
    score >= 60 -> Color(0xFFF59E0B)
    score >= 40 -> Color(0xFFEF4444)
    else        -> Color(0xFF6B7280)
}

internal fun privacyLabel(privacy: String) = when (privacy) {
    "FOLLOWERS" -> AppText.get(R.string.w_following)
    "WOMEN"     -> AppText.get(R.string.meetups_screen_v4_no_mixto)
    else        -> AppText.get(R.string.w_open)
}

internal fun disciplineLabel(discipline: String) = when (discipline) {
    "BOULDER" -> AppText.get(R.string.w_boulder)
    "ROUTE"   -> AppText.get(R.string.w_route)
    "BOTH"    -> AppText.get(R.string.meetups_screen_v4_bloque_via)
    else      -> discipline
}
