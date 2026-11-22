package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.setValue
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
fun SoloBoardSheet(
    dateLabel: String, date: LocalDate, onDismiss: () -> Unit, board: BoardKind? = null,
    /** Block opened from the detail sheet to edit or delete (its own day and gym). */
    edit: com.meteomontana.android.domain.model.gym.BoardEntry? = null,
    vm: GymTrainSheetViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(date, edit) { if (edit != null) vm.loadEdit(edit) else vm.load(GymSession.NO_GYM, date) }
    val commit = androidx.compose.runtime.remember { BoardCommit() }
    val editing = state.editing
    // Editing: the date can change too (the block moves to that day, same gym).
    var editDate by androidx.compose.runtime.remember(edit) { androidx.compose.runtime.mutableStateOf(edit?.day ?: date) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, shape = CumbreSheetShape
    ) {
        val hideKeyboard = com.meteomontana.android.ui.components.rememberKeyboardDismisser()
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { hideKeyboard() } }
        Column(Modifier.fillMaxWidth().fillMaxHeight().imePadding().cumbreSheetSurface().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(dateLabel.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (edit != null) stringResource(R.string.gym_board_edit_title, boardName(edit.board))
                        else if (board != null) stringResource(R.string.gym_solo_board_sheet, boardName(board)) else stringResource(R.string.gym_solo_board_title),
                        fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    stringResource(R.string.common_close), color = terraFillColor(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { hideKeyboard(); onDismiss() }.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Text(stringResource(if (edit != null) R.string.gym_board_edit_sub else R.string.gym_solo_board_sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.sm))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (editing != null) BoardEditDateRow(editDate) { editDate = it }
                if (edit == null) BoardsTab(
                    state.ascents, state.boardProblems, vm::adjust, { b, g, a, n, r, t -> vm.addBoard(b, g, a, n, r, t) }, vm.angles, board,
                    BoardNameAssist(state.suggestions, vm::onNameChanged, vm::clearSuggestions), showAdded = false, commit = commit
                ) else if (editing != null) BoardsTab(
                    emptyList(), emptyList(), vm::adjust, { _, g, a, n, r, t -> vm.saveEdit(editDate, g, a, n, r, t, onDismiss) }, vm.angles, editing.board,
                    BoardNameAssist(state.suggestions, vm::onNameChanged, vm::clearSuggestions), showAdded = false, commit = commit, initial = editing
                )
            }
            val total = state.ascents.sumOf { it.count }
            // Guardar añade lo que haya en el formulario (si hay grado) y lo guarda todo; sin grado y a cero, quita el entreno.
            val canSave = !state.saving && (if (edit != null) editing != null && commit.ready else commit.ready || total > 0 || state.hadAscents)
            Box(
                Modifier.fillMaxWidth().padding(top = Spacing.sm).clip(CircleShape).background(terraFillColor().copy(alpha = if (canSave) 1f else 0.4f))
                    .clickable(enabled = canSave) { hideKeyboard(); if (edit != null) commit.action() else { commit.action(); vm.save(GymSession.NO_GYM, onDismiss, date) } }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (if (total == 0 && state.hadAscents && !commit.ready) stringResource(R.string.gym_sheet_remove_workout) else stringResource(R.string.gym_board_save)).uppercase(),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
        }
    }
}
