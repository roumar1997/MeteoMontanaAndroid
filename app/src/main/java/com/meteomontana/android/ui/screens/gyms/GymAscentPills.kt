package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.AscentKey
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentKeys
import com.meteomontana.android.domain.model.gym.RouteStyle
import com.meteomontana.android.ui.theme.gradeChipStyle

/**
 * Las vías y los boards apuntados en `ascents` (claves `route:` y `board:`), siempre bonitos: nunca la clave en crudo.
 * Vía: grado con su color de grado, icono compañero o autoaseguro, «×N» y encadenada (check) o proyecto. Board: su
 * color de marca, el nombre, el grado con su color, la inclinación y «×N».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RouteBoardPills(ascents: List<GymAscent>, modifier: Modifier = Modifier) {
    val items = ascents.filter { GymAscentKeys.parse(it.key) !is AscentKey.Block }
    if (items.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { a ->
            when (val k = GymAscentKeys.parse(a.key)) {
                is AscentKey.Route -> RoutePill(k, a.count)
                is AscentKey.Board -> BoardPill(k, a.count)
                is AscentKey.Block -> Unit
            }
        }
    }
}

@Composable
private fun PillShell(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.background, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), content = content
    )
}

@Composable
private fun GradeDot(grade: String) {
    Box(Modifier.size(11.dp).clip(CircleShape).background(gradeChipStyle(grade).stroke).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
}

@Composable
private fun RoutePill(r: AscentKey.Route, count: Int) = PillShell {
    GradeDot(r.grade)
    Text(r.grade.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Icon(
        painterResource(if (r.style == RouteStyle.PARTNER) R.drawable.ic_style_partner else R.drawable.ic_autobelay), null,
        Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (r.project) Text(stringResource(R.string.gym_flow_project), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    if (count > 1) Text("×$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun BoardPill(b: AscentKey.Board, count: Int) = PillShell {
    Box(Modifier.size(12.dp).clip(CircleShape).background(boardBrandColor(b.board)))
    Text(boardName(b.board), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    GradeDot(b.grade)
    Text(b.grade.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    b.angle?.let { Text("$it°", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (count > 1) Text("×$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
}

/** Color de marca de cada board (el del logo de la hoja de entreno): Kilter negro, Tension madera, Moonboard amarillo. */
internal fun boardBrandColor(b: com.meteomontana.android.domain.model.gym.BoardKind): Color = when (b) {
    com.meteomontana.android.domain.model.gym.BoardKind.KILTER -> Color(0xFF1B1B1B)
    com.meteomontana.android.domain.model.gym.BoardKind.TENSION -> Color(0xFFC79A63)
    com.meteomontana.android.domain.model.gym.BoardKind.MOONBOARD -> Color(0xFFF2C21A)
    com.meteomontana.android.domain.model.gym.BoardKind.SYSTEM -> Color(0xFF3E8E6B)
}
