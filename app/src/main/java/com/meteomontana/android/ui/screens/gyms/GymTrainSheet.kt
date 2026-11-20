package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.AscentKey
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentKeys
import com.meteomontana.android.domain.model.gym.GymTraining
import com.meteomontana.android.domain.model.gym.RouteStyle
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import com.meteomontana.android.ui.screens.schools.TabButton
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.gradeChipStyle
import com.meteomontana.android.ui.theme.terraFillColor

private enum class TrainTab { BLOCKS, ROUTES, BOARDS }

private val KilterBlack = Color(0xFF1B1B1B)
private val KilterBlue = Color(0xFF2B7FFF)
private val TensionWood = Color(0xFFC79A63)
private val MoonYellow = Color(0xFFF2C21A)

/**
 * Hoja «He entrenado aquí hoy» (maqueta 2): pestañas Bloques | Vías | Boards, el rocódromo ya viene fijado y «Guardar
 * entreno» queda fijo abajo. Los grados llevan SIEMPRE la paleta de grados de la app. Lo hecho se apunta en `ascents`
 * con las claves de [GymAscentKeys].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GymTrainSheet(gym: Gym, dateLabel: String, onDismiss: () -> Unit, vm: GymTrainSheetViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(gym.id) { vm.load(gym.id) }
    var tab by remember { mutableStateOf(TrainTab.BLOCKS) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        shape = CumbreSheetShape
    ) {
        Column(Modifier.fillMaxWidth().cumbreSheetSurface().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md)) {
            Text(dateLabel.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.gym_sheet_title, gym.name), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = Spacing.sm))
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(3.dp)
            ) {
                TabButton(stringResource(R.string.gym_sheet_tab_blocks).uppercase(), tab == TrainTab.BLOCKS, Modifier.weight(1f)) { tab = TrainTab.BLOCKS }
                TabButton(stringResource(R.string.gym_sheet_tab_routes).uppercase(), tab == TrainTab.ROUTES, Modifier.weight(1f)) { tab = TrainTab.ROUTES }
                TabButton(stringResource(R.string.gym_sheet_tab_boards).uppercase(), tab == TrainTab.BOARDS, Modifier.weight(1f)) { tab = TrainTab.BOARDS }
            }
            Column(
                Modifier.weight(1f, fill = false).heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (tab) {
                    TrainTab.BLOCKS -> BlocksTab(gym, state.ascents, vm::adjust)
                    TrainTab.ROUTES -> RoutesTab(state.ascents, vm::adjust)
                    TrainTab.BOARDS -> BoardsTab(state.ascents, vm::adjust)
                }
            }
            val total = state.ascents.sumOf { it.count }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.gym_sheet_total).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pluralStringResource(R.plurals.gym_sheet_total_value, total, total), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            Box(
                Modifier.fillMaxWidth().padding(top = Spacing.sm).clip(CircleShape).background(terraFillColor())
                    .clickable(enabled = !state.saving) { vm.save(gym.id, onDismiss) }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.gym_sheet_save).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun pluralStringResource(id: Int, count: Int, vararg args: Any): String =
    androidx.compose.ui.res.pluralStringResource(id, count, *args)

// ── Bloques ───────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlocksTab(gym: Gym, ascents: List<GymAscent>, adjust: (String, Int) -> Unit) {
    val scale = gym.scale?.takeIf { it.isColors }
    val blocks = GymAscentKeys.blocks(ascents)
    Text(stringResource(if (scale != null) R.string.gym_sheet_hint_colors else R.string.gym_sheet_hint_grades), fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (scale != null) scale.levels.forEach { lv ->
        val n = GymTraining.countOf(blocks, lv.name)
        CounterRow(
            label = lv.name.replaceFirstChar { it.uppercase() }, sub = GymPresentation.gradeRange(lv.gradeFrom, lv.gradeTo),
            swatch = parseHex(lv.hex), accent = parseHex(lv.hex), count = n,
            onMinus = { adjust(lv.name, -1) }, onPlus = { adjust(lv.name, 1) }
        )
    } else GymAscentKeys.BLOCK_GRADES.forEach { g -> GradeCounterRow(g, GymTraining.countOf(blocks, g)) { d -> adjust(g, d) } }
}

// ── Vías ──────────────────────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutesTab(ascents: List<GymAscent>, adjust: (String, Int) -> Unit) {
    var grade by remember { mutableStateOf("6a") }
    var style by remember { mutableStateOf(RouteStyle.PARTNER) }
    var project by remember { mutableStateOf(false) }
    val routes = GymAscentKeys.routes(ascents)
    Eyebrow(stringResource(R.string.gym_sheet_grade))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GymAscentKeys.ROUTE_GRADES.forEach { g ->
            val badge = routes.filter { (GymAscentKeys.parse(it.key) as? AscentKey.Route)?.grade == g }.sumOf { it.count }
            GradeChoice(g, selected = g == grade, badge = badge) { grade = g }
        }
    }
    Eyebrow(stringResource(R.string.gym_sheet_how))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StyleCard(R.drawable.ic_style_partner, R.string.gym_style_partner, R.string.gym_style_partner_sub, style == RouteStyle.PARTNER, Modifier.weight(1f)) { style = RouteStyle.PARTNER }
        StyleCard(R.drawable.ic_autobelay, R.string.gym_style_solo, R.string.gym_style_solo_sub, style == RouteStyle.SOLO, Modifier.weight(1f)) { style = RouteStyle.SOLO }
    }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(3.dp)) {
        TabButton(stringResource(R.string.gym_flow_sent).uppercase(), !project, Modifier.weight(1f)) { project = false }
        TabButton(stringResource(R.string.gym_flow_project).uppercase(), project, Modifier.weight(1f)) { project = true }
    }
    val styleLabel = stringResource(if (style == RouteStyle.PARTNER) R.string.gym_style_partner else R.string.gym_style_solo)
    Box(
        Modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable { adjust(GymAscentKeys.route(style, project, grade), 1) }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("+  " + stringResource(R.string.gym_sheet_add_route, grade.uppercase(), styleLabel).uppercase(), fontSize = 12.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
    }
    val summary = GymAscentKeys.routeSummary(ascents)
    if (summary.isNotEmpty()) {
        Eyebrow(stringResource(R.string.gym_sheet_today))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            routes.forEach { a ->
                val r = GymAscentKeys.parse(a.key) as? AscentKey.Route ?: return@forEach
                Row(
                    Modifier.clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { adjust(a.key, -1) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("${r.grade.uppercase()} ×${a.count}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(painterResource(if (r.style == RouteStyle.PARTNER) R.drawable.ic_style_partner else R.drawable.ic_autobelay), null,
                        Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.project) Text(stringResource(R.string.gym_flow_project), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(stringResource(R.string.gym_sheet_tap_to_remove), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Boards ────────────────────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardsTab(ascents: List<GymAscent>, adjust: (String, Int) -> Unit) {
    var board by remember { mutableStateOf(BoardKind.KILTER) }
    var angle by remember { mutableStateOf<Int?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BoardKind.entries.forEach { b ->
            val sel = b == board
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (sel) boardColor(b).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                    .border(if (sel) 2.dp else 1.dp, if (sel) boardColor(b) else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { board = b }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BoardLogo(b)
                Text(boardName(b), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
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
    Eyebrow(stringResource(R.string.gym_sheet_boulders_by_grade))
    val mine = GymAscentKeys.boards(ascents, board)
    GymAscentKeys.BLOCK_GRADES.forEach { g ->
        val key = GymAscentKeys.board(board, g, angle)
        GradeCounterRow(g, GymTraining.countOf(mine, key)) { d -> adjust(key, d) }
    }
    Text(stringResource(R.string.gym_sheet_board_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun boardColor(b: BoardKind) = when (b) { BoardKind.KILTER -> KilterBlue; BoardKind.TENSION -> TensionWood; BoardKind.MOONBOARD -> MoonYellow }

@Composable
private fun boardName(b: BoardKind) = stringResource(when (b) {
    BoardKind.KILTER -> R.string.gym_board_kilter; BoardKind.TENSION -> R.string.gym_board_tension; BoardKind.MOONBOARD -> R.string.gym_board_moon
})

/** Logo-color de cada board: Kilter negro con celeste, Tension madera, Moonboard amarillo. */
@Composable
private fun BoardLogo(b: BoardKind) {
    val base = when (b) { BoardKind.KILTER -> KilterBlack; BoardKind.TENSION -> TensionWood; BoardKind.MOONBOARD -> MoonYellow }
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(base), contentAlignment = Alignment.Center) {
        if (b == BoardKind.KILTER) Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF58B7FF)))
    }
}

// ── Piezas ────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 4.dp))

@Composable
private fun GradeChoice(grade: String, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val st = gradeChipStyle(grade)
    Box {
        Text(
            grade.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) (if (st.dark) Color(0xFF1B1B1B) else Color.White) else MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 38.dp).widthIn48().clip(RoundedCornerShape(12.dp))
                .background(if (selected) st.stroke else MaterialTheme.colorScheme.surface)
                .border(if (selected) 0.dp else 2.dp, st.stroke, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp)
        )
        if (badge > 0) Text(
            "$badge", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(KilterBlack).padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

private fun Modifier.widthIn48() = this.then(Modifier.heightIn(min = 38.dp))

@Composable
private fun StyleCard(icon: Int, title: Int, sub: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(if (selected) terraFillColor().copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) terraFillColor() else MaterialTheme.colorScheme.outline), shape)
            .clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(painterResource(icon), null, Modifier.size(26.dp), tint = if (selected) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Text(stringResource(sub), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Fila con contador − n +: color (bolita) o grado, y su nombre. */
@Composable
private fun CounterRow(label: String, sub: String?, swatch: Color?, accent: Color, count: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(if (count > 0) 2.dp else 1.dp, if (count > 0) accent else MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        swatch?.let { Box(Modifier.size(26.dp).clip(CircleShape).background(it).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)) }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            sub?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        RoundButton("−", onMinus, enabled = count > 0)
        Text("$count", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.widthIn(min = 24.dp), textAlign = TextAlign.Center)
        RoundButton("+", onPlus, enabled = true)
    }
}

@Composable
private fun GradeCounterRow(grade: String, count: Int, onDelta: (Int) -> Unit) =
    CounterRow(grade.uppercase(), null, gradeChipStyle(grade).stroke, gradeChipStyle(grade).stroke, count, { onDelta(-1) }, { onDelta(1) })

@Composable
private fun RoundButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = if (enabled) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}
