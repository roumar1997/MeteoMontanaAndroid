package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.AscentKey
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.BoardProblems
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentKeys
import com.meteomontana.android.domain.model.gym.RouteStyle
import com.meteomontana.android.ui.screens.schools.TabButton
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.gradeChipStyle
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Pestaña «Vías» de la hoja de entreno: lo añadido (con papelera), la rejilla de grados (tocar uno, o dos contiguos para
 * un grado doble 6B+/6C), cómo se hizo y «Añadir». Se pueden añadir muchas seguidas sin salir de la hoja.
 */
@Composable
internal fun RoutesTab(ascents: List<GymAscent>, adjust: (String, Int) -> Unit) {
    var selected by remember { mutableStateOf(listOf<String>()) }
    var style by remember { mutableStateOf(RouteStyle.PARTNER) }
    var project by remember { mutableStateOf(false) }
    val routes = GymAscentKeys.routes(ascents)
    AddedList(routes, adjust)
    Eyebrow(stringResource(R.string.gym_sheet_grade_pick))
    GradeGrid(selected) { selected = GymAscentKeys.toggleGrade(selected, it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StyleCard(R.drawable.ic_style_partner, R.string.gym_style_partner, R.string.gym_style_partner_sub, style == RouteStyle.PARTNER, Modifier.weight(1f)) { style = RouteStyle.PARTNER }
        StyleCard(R.drawable.ic_autobelay, R.string.gym_style_solo, R.string.gym_style_solo_sub, style == RouteStyle.SOLO, Modifier.weight(1f)) { style = RouteStyle.SOLO }
    }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(3.dp)) {
        TabButton(stringResource(R.string.gym_flow_sent).uppercase(), !project, Modifier.weight(1f)) { project = false }
        TabButton(stringResource(R.string.gym_flow_project).uppercase(), project, Modifier.weight(1f)) { project = true }
    }
    val grade = GymAscentKeys.gradeOf(selected)
    AddButton(grade) {
        grade?.let { adjust(GymAscentKeys.route(style, project, it), 1); selected = emptyList() }
    }
}

/** Pestaña «Boards»: el board (Kilter, Tension, Moonboard), la inclinación, los grados y lo ya añadido. */
@Composable
internal fun BoardsTab(
    ascents: List<GymAscent>, problems: List<BoardProblem>, adjust: (String, Int) -> Unit,
    addBoard: (BoardKind, String, Int?, String) -> Unit
) {
    var board by remember { mutableStateOf(BoardKind.KILTER) }
    var angle by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    var name by remember { mutableStateOf("") }
    val boardItems = ascents.filter { GymAscentKeys.parse(it.key) is AscentKey.Board }
    AddedList(boardItems, adjust, BoardProblems.namesByKey(boardItems, problems))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BoardKind.entries.forEach { b ->
            val sel = b == board
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (sel) boardColor(b).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                    .border(if (sel) 2.dp else 1.dp, if (sel) boardColor(b) else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { board = b }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                BoardLogo(b)
                Text(boardName(b), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(stringResource(R.string.gym_sheet_incline), Modifier.weight(1f))
        GymAscentKeys.BOARD_ANGLES.forEach { a ->
            val sel = angle == a
            Text("$a°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clip(CircleShape).background(if (sel) terraFillColor() else MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { angle = if (sel) null else a }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    Eyebrow(stringResource(R.string.gym_sheet_grade_pick))
    GradeGrid(selected) { selected = GymAscentKeys.toggleGrade(selected, it) }
    OutlinedTextField(
        value = name, onValueChange = { name = it.take(BoardProblems.MAX_NAME) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.gym_board_name_hint)) }, shape = RoundedCornerShape(14.dp),
        supportingText = { Text("${name.length}/${BoardProblems.MAX_NAME}", fontSize = 11.sp) }
    )
    val grade = GymAscentKeys.gradeOf(selected)
    AddButton(grade) {
        grade?.let { addBoard(board, it, angle, name); selected = emptyList(); name = "" }
    }
    Text(stringResource(R.string.gym_sheet_board_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Rejilla de todos los grados con su color; los elegidos (uno o dos contiguos) llevan aro terracota. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeGrid(selected: List<String>, onTap: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GymAscentKeys.GRID_GRADES.forEach { g -> GradeChip(g, g in selected) { onTap(g) } }
    }
}

@Composable
private fun GradeChip(grade: String, selected: Boolean, onClick: () -> Unit) {
    val st = gradeChipStyle(grade)
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier.size(width = 46.dp, height = 36.dp).clip(shape).background(st.stroke)
            .border(if (selected) 3.dp else 0.dp, if (selected) Terra else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(grade.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (st.dark) Color(0xFF1B1B1B) else Color.White)
    }
}

/** Botón discontinuo «+ AÑADIR 6B+/6C»; sin grado elegido queda apagado. */
@Composable
private fun AddButton(grade: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape).border(1.5.dp, Terra.copy(alpha = if (grade != null) 1f else 0.35f), shape)
            .clickable(enabled = grade != null, onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "+  " + (grade?.let { stringResource(R.string.gym_add_grade, it.uppercase()) } ?: stringResource(R.string.gym_add_pick)).uppercase(),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            color = Terra.copy(alpha = if (grade != null) 1f else 0.5f)
        )
    }
}

/** Lo añadido hoy en esta pestaña: grado con su color, qué es, cuántas veces y la papelera que quita la fila entera. */
@Composable
private fun AddedList(list: List<GymAscent>, adjust: (String, Int) -> Unit, names: Map<String, List<String>> = emptyMap()) {
    if (list.isEmpty()) return
    Eyebrow(stringResource(R.string.gym_added_today, list.sumOf { it.count }))
    list.forEach { a ->
        val (grade, title, sub) = describe(a) ?: return@forEach
        val st = gradeChipStyle(GymAscentKeys.baseGrade(grade))
        val shape = RoundedCornerShape(14.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(grade.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (st.dark) Color(0xFF1B1B1B) else Color.White,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(st.stroke).padding(horizontal = 10.dp, vertical = 6.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                Text(listOf(sub).plus(names[a.key].orEmpty()).joinToString(" \u00b7 "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text("×${a.count}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            Icon(
                Icons.Outlined.Delete, stringResource(R.string.gym_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable { adjust(a.key, -a.count) }.padding(7.dp)
            )
        }
    }
}

/** Grado, título y subtítulo de una fila añadida (vía o board); null si la clave no es de ninguno. */
@Composable
private fun describe(a: GymAscent): Triple<String, String, String>? = when (val k = GymAscentKeys.parse(a.key)) {
    is AscentKey.Route -> {
        val style = stringResource(if (k.style == RouteStyle.PARTNER) R.string.gym_style_partner else R.string.gym_style_solo)
        val flow = stringResource(if (k.project) R.string.gym_flow_project else R.string.gym_flow_sent)
        Triple(k.grade, stringResource(if ("/" in k.grade) R.string.gym_route_double else R.string.gym_route_single), "$flow · $style")
    }
    is AscentKey.Board ->
        Triple(k.grade, stringResource(R.string.gym_board_row, boardName(k.board)), k.angle?.let { "$it°" } ?: stringResource(R.string.gym_board_no_angle))
    else -> null
}

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 4.dp))

/** Tarjeta de elección (con compañero / autoaseguro). */
@Composable
internal fun StyleCard(icon: Int, title: Int, sub: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(if (selected) terraFillColor().copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
            .border(androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) terraFillColor() else MaterialTheme.colorScheme.outline), shape)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = if (selected) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Text(stringResource(sub), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
