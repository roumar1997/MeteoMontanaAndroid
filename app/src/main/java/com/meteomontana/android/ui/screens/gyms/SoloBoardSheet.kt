package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor
import kotlinx.datetime.LocalDate

/**
 * «Solo board»: apunta boards SIN rocódromo (tablero de casa o da igual dónde) con el mismo registro que «He entrenado»
 * (tablero, inclinación, grado, nombre opcional). Con [board] el tablero viene fijado (el «+» de la pantalla Boards). Se guarda
 * como sesión sin rocódromo ([GymSession.NO_GYM]): primero en el móvil, funciona sin red y se sube al volver la conexión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoloBoardSheet(dateLabel: String, date: LocalDate, onDismiss: () -> Unit, board: BoardKind? = null, vm: GymTrainSheetViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(date) { vm.load(GymSession.NO_GYM, date) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, shape = CumbreSheetShape
    ) {
        Column(Modifier.fillMaxWidth().cumbreSheetSurface().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md)) {
            Text(dateLabel.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Text(
                if (board != null) stringResource(R.string.gym_solo_board_sheet, boardName(board)) else stringResource(R.string.gym_solo_board_title),
                fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground
            )
            Text(stringResource(R.string.gym_solo_board_sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.sm))
            Column(
                Modifier.weight(1f, fill = false).heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { BoardsTab(state.ascents, state.boardProblems, vm::adjust, vm::addBoard, vm.angles, board, BoardNameAssist(state.suggestions, vm::onNameChanged, vm::clearSuggestions)) }
            val total = state.ascents.sumOf { it.count }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.gym_sheet_total).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pluralStringResource(R.plurals.gym_sheet_total_value, total, total), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            val canSave = !state.saving && (total > 0 || state.hadAscents)
            Box(
                Modifier.fillMaxWidth().padding(top = Spacing.sm).clip(CircleShape).background(terraFillColor().copy(alpha = if (canSave) 1f else 0.4f))
                    .clickable(enabled = canSave) { vm.save(GymSession.NO_GYM, onDismiss, date) }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (if (total == 0 && state.hadAscents) stringResource(R.string.gym_sheet_remove_workout) else stringResource(R.string.gym_solo_board_save) + (if (total > 0) " · $total" else "")).uppercase(),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
        }
    }
}
