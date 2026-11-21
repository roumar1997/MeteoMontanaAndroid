package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.AscentFilter
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymBreakdown
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.domain.model.gym.GymSummaryGym
import com.meteomontana.android.ui.screens.gyms.BoardLogo
import com.meteomontana.android.ui.screens.gyms.boardName
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.terraFillColor

// Desglose de Ascensiones: cada rocódromo tiene SU escala (colores o grados), así que nunca se mezclan en un mismo reparto.

/**
 * Cuerpo de «Ascensiones» según la pestaña: Boards agrupado por tablero; Todo, Bloques y Vías agrupados por rocódromo
 * (si el servidor aún no manda el desglose por rocódromo, se pinta como antes: un solo reparto por grado).
 */
@Composable
internal fun AscentsBody(
    s: GymSummary, filter: AscentFilter, items: List<GymAscent>,
    colorHex: (String) -> String?, colorHexFor: (Long, String) -> String?, onOpenGym: (Long) -> Unit
) {
    when {
        filter == AscentFilter.BOARDS -> BoardsSections(items)
        GymBreakdown.hasGymBreakdown(s) -> {
            val gyms = GymBreakdown.gymsFiltered(s, filter)
            if (gyms.isEmpty()) EmptyCategory() else PerGymSections(gyms, colorHexFor, onOpenGym)
            if (filter == AscentFilter.ALL) ByMonthSection(s)
        }
        else -> {
            if (filter == AscentFilter.ALL) ByGymSection(s, onOpenGym)
            ByGradeSection(items, colorHex)
            if (filter == AscentFilter.ALL) ByMonthSection(s)
        }
    }
}

@Composable
private fun EmptyCategory() = Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
    Text(stringResource(R.string.gym_asc_empty_category), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Una sección por rocódromo: cabecera (inicial, nombre, cuántas, flecha; abre lo hecho allí) y su reparto con SU escala. */
@Composable
internal fun PerGymSections(gyms: List<GymSummaryGym>, colorHexFor: (Long, String) -> String?, onOpenGym: (Long) -> Unit) {
    gyms.forEach { g ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable { onOpenGym(g.gymId) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(terraFillColor()), contentAlignment = Alignment.Center) {
                    Text((g.name ?: "?").take(1).uppercase(), color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(
                    g.name ?: "—", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
                )
                Text(g.ascents.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ByGradeSection(g.items, { key -> colorHexFor(g.gymId, key) }, titled = false)
        }
    }
}

/** Boards agrupados por tablero (logo, nombre y total) y, dentro, el reparto por grado. */
@Composable
internal fun BoardsSections(items: List<GymAscent>) {
    val groups = GymBreakdown.boardsByKind(items)
    if (groups.isEmpty()) { EmptyCategory(); return }
    groups.forEach { (kind, list) ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoardLogo(kind)
                Text(boardName(kind), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Text(list.sumOf { it.count }.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            ByGradeSection(list, { null }, titled = false)
        }
    }
}
