package com.meteomontana.android.ui.meetups

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonCard
import com.meteomontana.android.ui.components.SkeletonPersonRow
import com.meteomontana.android.util.AppText
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.MeetupMember
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.core.ui.R
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.SourceSerif4Family
import com.meteomontana.android.ui.theme.Spacing

// Material de una quedada: leer y sumar el de cada persona, la sección, el diálogo para editarlo y sus controles.

fun parseGear(json: String?): Map<String, Int> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        json.trim('{', '}').split(",").mapNotNull { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim().trim('"')
                val value = parts[1].trim().toIntOrNull() ?: 0
                if (value > 0) key to value else null
            } else null
        }.toMap()
    } catch (_: Exception) { emptyMap() }
}

fun buildGearJson(gear: Map<String, Int>): String {
    val entries = gear.filter { it.value > 0 }
    if (entries.isEmpty()) return "{}"
    return "{" + entries.entries.joinToString(",") { "\"${it.key}\":${it.value}" } + "}"
}

fun gearItemsForDiscipline(discipline: String?): List<Pair<String, String>> = when (discipline) {
    "BOULDER" -> listOf("crashpads" to AppText.get(R.string.gear_crashpads))
    "ROUTE" -> listOf("cintas" to AppText.get(R.string.gear_quickdraws), "cuerda" to AppText.get(R.string.gear_ropes), "grigri" to AppText.get(R.string.gear_grigri))
    else -> listOf("crashpads" to AppText.get(R.string.gear_crashpads), "cintas" to AppText.get(R.string.gear_quickdraws),
        "cuerda" to AppText.get(R.string.gear_ropes), "grigri" to AppText.get(R.string.gear_grigri))
}

fun gearLabel(key: String, count: Int = 2): String = when (key) {
    "crashpads" -> if (count == 1) "crashpad" else "crashpads"
    "cintas" -> if (count == 1) "cinta" else "cintas"
    "cuerda" -> if (count == 1) "cuerda" else "cuerdas"
    "grigri" -> "gri-gri"
    else -> key
}

/**
 * Cuerda y grigri son "sí/no" por persona (o los llevas o no); cintas y
 * crashpads son cantidad (cuántas llevas). Al sumar entre miembros de una
 * quedada, cada "sí" cuenta como 1 → "5 cuerdas" si 5 personas dijeron que sí.
 */
fun isBooleanGearKey(key: String): Boolean = key == "cuerda" || key == "grigri"

/** Suma el material de todos los miembros. */
fun totalGear(members: List<MeetupMember>): Map<String, Int> {
    val totals = mutableMapOf<String, Int>()
    members.forEach { m ->
        parseGear(m.gearJson).forEach { (k, v) ->
            totals[k] = (totals[k] ?: 0) + v
        }
    }
    return totals.filter { it.value > 0 }
}

/** Texto compacto del material de un miembro (e.g. "2 crashpads, 12 cintas"). */
fun gearSummaryText(gearJson: String?): String {
    val gear = parseGear(gearJson)
    if (gear.isEmpty()) return ""
    return gear.entries.joinToString(" · ") { "${it.value} ${gearLabel(it.key, it.value)}" }
}

/** Texto compacto del total (para el banner del chat). */
fun totalGearSummary(members: List<MeetupMember>): String {
    val totals = totalGear(members)
    if (totals.isEmpty()) return ""
    return totals.entries.joinToString(" · ") { "${it.value} ${gearLabel(it.key, it.value)}" }
}

// ── Gear section composable ─────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GearSection(
    meetup: com.meteomontana.android.domain.model.Meetup,
    myUid: String?,
    isCreator: Boolean,
    onEditGear: () -> Unit
) {
    val totals = totalGear(meetup.members)

    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Eyebrow
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(Icons.Outlined.Luggage, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.meetup_detail_material), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Total pills
        if (totals.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                totals.forEach { (key, count) ->
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("$count", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(gearLabel(key, count), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text(stringResource(R.string.meetup_detail_no_material),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Per-member breakdown
        meetup.members.forEach { member ->
            val gear = gearSummaryText(member.gearJson)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MemberAvatar(member.photoUrl, 28.dp)
                Text(member.displayName ?: member.username ?: member.uid.take(6),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false))
                if (gear.isNotEmpty()) {
                    Text(gear, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.meetup_detail_no_gear), style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }

        // Edit button (only if joined or creator)
        if (meetup.joined || isCreator) {
            OutlinedButton(
                onClick = onEditGear,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.meetup_detail_edit_gear), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Sí/no para material que no se cuenta (cuerda, grigri): lo llevas o no. */
@Composable
fun GearToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
fun GearStepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconButton(
                onClick = onMinus,
                modifier = Modifier.size(32.dp),
                enabled = value > 0
            ) {
                Icon(Icons.Outlined.Remove, stringResource(R.string.w_less), Modifier.size(18.dp))
            }
            Text("$value", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(
                onClick = onPlus,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Outlined.Add, stringResource(R.string.w_more), Modifier.size(18.dp))
            }
        }
    }
}
