package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblems
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.domain.model.gym.BoardProblemRepeats
import com.meteomontana.android.domain.model.gym.RepeatedProblem
import com.meteomontana.android.ui.screens.gyms.boardName
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.gradeChipStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Bloques de board con nombre (opcional) dentro de cada tablero de Ascensiones: buscador por nombre y lista con grado y
// dia. La busqueda y el orden salen de `shared` (BoardProblems); aqui solo se pinta. Vale para el perfil propio y el ajeno.

/** Buscador + lista de bloques con nombre de UN tablero. Sin texto enseña todos los nombrados; con texto, los que coinciden. */
@Composable
internal fun BoardNamedSection(kind: BoardKind, summary: GymSummary, onOpenGym: (Long) -> Unit, boardItems: List<com.meteomontana.android.domain.model.gym.GymAscent> = emptyList(), angle: Int? = null) {
    var query by rememberSaveable(kind.name) { mutableStateOf("") }
    OutlinedTextField(
        value = query, onValueChange = { query = it.take(BoardProblems.MAX_NAME) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        placeholder = { Text(stringResource(R.string.gym_board_search_hint, boardName(kind)), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) }
    )
    val pool = com.meteomontana.android.domain.model.gym.BoardAngles.problemsAt(summary.boardProblems, boardItems, kind, angle)
    val found = BoardProblemRepeats.repeated(pool, kind, query)
    if (found.isEmpty()) {
        if (query.isNotBlank()) Text(stringResource(R.string.gym_board_search_empty), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        return
    }
    Text(stringResource(R.string.gym_board_named_title, found.size).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        found.forEach { p -> NamedRow(p, summary.byGym.firstOrNull { it.gymId == p.lastGymId }?.name, onOpenGym) }
    }
}

@Composable
private fun NamedRow(p: RepeatedProblem, gymName: String?, onOpenGym: (Long) -> Unit) {
    val st = gradeChipStyle(p.grade.substringBefore('/'))
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = p.lastGymId != com.meteomontana.android.domain.model.gym.GymSession.NO_GYM) { onOpenGym(p.lastGymId) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            p.grade.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, color = if (st.dark) Color(0xFF1B1B1B) else Color.White,
            modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(st.stroke).padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            Text(listOfNotNull(gymName, if (p.reps > 1) pluralStringResource(R.plurals.gym_board_reps_days, p.dates.size, p.dates.size) else null, stringResource(R.string.gym_board_reps_last, dayLabel(p.lastDate))).joinToString(" · "), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (p.reps > 1) Text(stringResource(R.string.gym_board_reps_badge, p.reps), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, color = MaterialTheme.colorScheme.primary)
    }
}

private fun dayLabel(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())) }.getOrDefault(iso)
