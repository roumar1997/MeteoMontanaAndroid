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
private val SystemGreen = Color(0xFF3E8E6B)

/**
 * Hoja «He entrenado aquí hoy» (maqueta 2): pestañas Bloques | Vías | Boards, el rocódromo ya viene fijado y «Guardar
 * entreno» queda fijo abajo. Los grados llevan SIEMPRE la paleta de grados de la app. Lo hecho se apunta en `ascents`
 * con las claves de [GymAscentKeys].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GymTrainSheet(gym: Gym, dateLabel: String, onDismiss: () -> Unit, date: kotlinx.datetime.LocalDate? = null, vm: GymTrainSheetViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(gym.id, date) { vm.load(gym.id, date) }
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
                    TrainTab.BOARDS -> BoardsTab(state.ascents, state.boardProblems, vm::adjust, vm::addBoard, vm.angles, assist = BoardNameAssist(state.suggestions, vm::onNameChanged, vm::clearSuggestions))
                }
            }
            val total = state.ascents.sumOf { it.count }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.gym_sheet_total).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pluralStringResource(R.plurals.gym_sheet_total_value, total, total), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            Box(
                Modifier.fillMaxWidth().padding(top = Spacing.sm).clip(CircleShape)
                    .background(terraFillColor().copy(alpha = if (total > 0 || state.hadAscents) 1f else 0.4f))
                    .clickable(enabled = !state.saving && (total > 0 || state.hadAscents)) { vm.save(gym.id, onDismiss, date) }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (if (total == 0 && state.hadAscents) stringResource(R.string.gym_sheet_remove_workout)
                    else stringResource(R.string.gym_sheet_save) + (if (total > 0) " · $total" else "")).uppercase(),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
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

internal fun boardColor(b: BoardKind) = when (b) { BoardKind.KILTER -> KilterBlue; BoardKind.TENSION -> TensionWood; BoardKind.MOONBOARD -> MoonYellow; BoardKind.SYSTEM -> SystemGreen }

@Composable
internal fun boardName(b: BoardKind) = stringResource(when (b) {
    BoardKind.KILTER -> R.string.gym_board_kilter; BoardKind.TENSION -> R.string.gym_board_tension; BoardKind.MOONBOARD -> R.string.gym_board_moon; BoardKind.SYSTEM -> R.string.gym_board_system
})

/** Logo-color de cada board: Kilter negro con celeste, Tension madera, Moonboard amarillo. */
@Composable
internal fun BoardLogo(b: BoardKind) {
    val base = when (b) { BoardKind.KILTER -> KilterBlack; BoardKind.TENSION -> TensionWood; BoardKind.MOONBOARD -> MoonYellow; BoardKind.SYSTEM -> SystemGreen }
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(base), contentAlignment = Alignment.Center) {
        if (b == BoardKind.KILTER) Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF58B7FF)))
    }
}

// ── Piezas ────────────────────────────────────────────────────────────────────────────────────────────────────

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
