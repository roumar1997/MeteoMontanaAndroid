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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.meteomontana.android.domain.model.MeetupGearStatus
import com.meteomontana.android.ui.components.userRing
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
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = 5.dp)
            .clip(shape).background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            MeetupCover(meetup)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(meetup.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    fontFamily = com.meteomontana.android.ui.theme.SourceSerif4Family, maxLines = 2)
                meetup.creatorUsername?.let {
                    Text("@$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            MeetupListBadges(meetup)
        }
        MeetupPlaceCard(meetup, dayScoresMap, distanceKm)
        // Días con su nota
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            meetup.days.forEach { day ->
                val score = dayScoresMap["${meetup.schoolId}_$day"]
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(formatDayMonth(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (score != null) Box(Modifier.clip(RoundedCornerShape(50)).background(scoreColor(score)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("$score", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Modalidad, cordada y privacidad
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            meetup.discipline?.let { MeetupMiniPill(disciplineLabel(it)) }
            if (meetup.isRopeTeam) MeetupMiniPill(stringResource(R.string.meetup_rope_route_label), highlight = true)
            if (meetup.privacy != "OPEN") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(privacyLabel(meetup.privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        MeetupGearStatusRow(meetup)
    }
}

/** Foto de la quedada; sin foto, el avatar del organizador con su aureola de título (nunca un icono vacío). */
@Composable
private fun MeetupCover(meetup: Meetup) {
    val photo = meetup.photoUrl
    val avatar = meetup.creatorPhotoUrl
    when {
        photo != null -> AsyncImage(
            model = photo, contentDescription = meetup.name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
        )
        avatar != null -> AsyncImage(
            model = avatar, contentDescription = meetup.creatorUsername, contentScale = ContentScale.Crop,
            modifier = Modifier.padding(3.dp).userRing(meetup.creatorUid, 54.dp).size(54.dp).clip(CircleShape)
        )
        else -> Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Groups, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MeetupListBadges(meetup: Meetup) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            meetup.joined -> Box(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = Spacing.sm, vertical = 2.dp)) {
                Text(stringResource(R.string.meetups_joined).uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            meetup.isFull -> Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)).padding(horizontal = Spacing.sm, vertical = 2.dp)) {
                Text(stringResource(R.string.meetups_full).uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Outlined.Person, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(meetup.memberLimit?.let { "${meetup.memberCount}/$it" } ?: "${meetup.memberCount}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Tarjeta del lugar dentro de la quedada, como las de la app: en ROCA, el anillo de nota de la escuela (la del primer día
 * con previsión; mojada = gota) y su nombre con la distancia; en ROCÓDROMO, el cuadrado del rocódromo como en su lista.
 */
@Composable
private fun MeetupPlaceCard(meetup: Meetup, dayScoresMap: Map<String, Int>, distanceKm: Double?) {
    val name = (meetup.placeName ?: meetup.schoolName)?.takeIf { it.isNotBlank() } ?: return
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (meetup.isGymMeetup) com.meteomontana.android.ui.screens.gyms.GymKindBadge(
            com.meteomontana.android.domain.model.gym.GymKind.COMMERCIAL, size = 40.dp
        ) else com.meteomontana.android.ui.components.ScoreRing(meetup.days.firstNotNullOfOrNull { dayScoresMap["${meetup.schoolId}_$it"] }, diameter = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2,
                color = MaterialTheme.colorScheme.onBackground)
            if (distanceKm != null) Text("${distanceKm.toInt()} km", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MeetupMiniPill(text: String, highlight: Boolean = false) {
    val color = if (highlight) com.meteomontana.android.ui.theme.terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

/**
 * Material del grupo SIN abrir la quedada: lo que ya hay en verde («✓ Cuerda») y lo que falta en rojo («Falta gri-gri»),
 * para saber qué llevar antes de apuntarse. Sin material pedido no se pinta nada. Lógica en shared ([MeetupGearStatus]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetupGearStatusRow(meetup: Meetup) {
    val lines = MeetupGearStatus.lines(meetup)
    if (lines.isEmpty()) return
    val ok = com.meteomontana.android.ui.theme.Ok
    val bad = MaterialTheme.colorScheme.error
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.meetup_list_material).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lines.forEach { l ->
            val label = stringResource(gearLabelRes(l.item))
            val text = if (l.ok) "✓ $label" else stringResource(R.string.meetup_list_gear_missing, label.lowercase())
            val c = if (l.ok) ok else bad
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c, maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(c.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 3.dp))
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
                        items(results.distinctBy { it.id }, key = { it.id }) { school ->
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
