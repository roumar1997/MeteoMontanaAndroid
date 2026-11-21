package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.AscentEntry
import com.meteomontana.android.domain.model.gym.AscentKey
import com.meteomontana.android.domain.model.gym.GymAscentKeys
import com.meteomontana.android.ui.screens.gyms.boardName
import com.meteomontana.android.ui.theme.gradeChipStyle
import com.meteomontana.android.ui.theme.terraFillColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Filtro por grado de Ascensiones: una fila de chips («Todos» + los grados de la categoria) y, con alguno elegido, la lista
// de ascensiones sueltas de esos grados. La lista sale de `shared` (AscentEntries); aqui solo se pinta.

/** Fila de chips de grado (varios a la vez); «Todos» limpia la eleccion. [grades] ya vienen de facil a dificil. */
@Composable
internal fun GradeChipRow(grades: List<String>, selected: Set<String>, onToggle: (String) -> Unit, onClear: () -> Unit) {
    if (grades.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GradeChip(stringResource(R.string.gym_grade_all), selected.isEmpty(), terraFillColor(), Color.White, onClear)
        grades.forEach { g ->
            val st = gradeChipStyle(g)
            GradeChip(g.uppercase(), g in selected, st.stroke, if (st.dark) Color(0xFF1B1B1B) else Color.White) { onToggle(g) }
        }
    }
}

@Composable
private fun GradeChip(label: String, active: Boolean, color: Color, onColor: Color, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier.clip(shape).background(if (active) color else Color.Transparent, shape).border(1.5.dp, color, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, color = if (active) onColor else MaterialTheme.colorScheme.onBackground)
    }
}

/** Las ascensiones sueltas de los grados elegidos: grado, que fue (bloque, via o board), donde y cuando (o «Antes»). */
@Composable
internal fun GradeEntriesList(entries: List<AscentEntry>, onOpenGym: (Long) -> Unit) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.gym_asc_empty_category), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        entries.forEach { e -> EntryRow(e, onOpenGym) }
    }
}

@Composable
private fun EntryRow(e: AscentEntry, onOpenGym: (Long) -> Unit) {
    val grade = GymAscentKeys.gradeOfKey(e.key)
    val st = gradeChipStyle(grade)
    val kind = when (val k = GymAscentKeys.parse(e.key)) {
        is AscentKey.Board -> boardName(k.board)
        is AscentKey.Route -> stringResource(R.string.gym_route_single)
        is AscentKey.Block -> stringResource(R.string.gym_entry_block)
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable { onOpenGym(e.gymId) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            (grade ?: "").uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, color = if (st.dark) Color(0xFF1B1B1B) else Color.White,
            modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(st.stroke).padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(if (e.names.isEmpty()) kind else kind + " \u00b7 " + e.names.joinToString(", "), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            Text(
                listOfNotNull(e.gymName, dayText(e.date)).joinToString(" · "), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (e.count > 1) Text("×${e.count}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun dayText(iso: String?): String =
    if (iso == null) stringResource(R.string.gym_entry_before)
    else runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())) }.getOrDefault(iso)
