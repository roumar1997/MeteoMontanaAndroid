package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.AscentEntries
import com.meteomontana.android.domain.model.gym.AscentFilter
import com.meteomontana.android.domain.model.gym.GymBreakdown
import com.meteomontana.android.ui.theme.Serif

/**
 * «Boards · N», propia o de otra persona: TODOS los boards de todos los rocódromos juntos (un problema de Kilter, MoonBoard,
 * Tension o System es el mismo en cualquier sala). Una sección plegable por tablero con su reparto por grado, buscador por
 * nombre y filtro por grado; son las mismas piezas que la pestaña Boards de Ascensiones. Misma privacidad que `gym-summary`.
 */
@Composable
fun BoardsScreen(
    uid: String?,
    onBack: () -> Unit,
    onOpenGym: (Long) -> Unit = {},
    viewModel: ProfileClimbViewModel = hiltViewModel()
) {
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(uid) { viewModel.load(uid, myUid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.summary
    val items = GymBreakdown.filter(s.items, AscentFilter.BOARDS)
    var grades by remember { mutableStateOf(emptyList<String>()) }
    // «+» de cada tablero (solo en el perfil propio): abre el registro de boards de HOY con ese tablero fijado, sin rocodromo.
    val own = uid == null || uid == myUid
    var addFor by remember { mutableStateOf<com.meteomontana.android.domain.model.gym.BoardKind?>(null) }
    val onAddBoard: ((com.meteomontana.android.domain.model.gym.BoardKind) -> Unit)? = if (own) { k -> addFor = k } else null
    // Own profile: a day of a block's detail opens the same sheet prefilled, with GUARDAR and Borrar (§26).
    var editFor by remember { mutableStateOf<com.meteomontana.android.domain.model.gym.BoardEntry?>(null) }
    var deleteFor by remember { mutableStateOf<com.meteomontana.android.domain.model.gym.BoardEntry?>(null) }
    val boardSheetVm: com.meteomontana.android.ui.screens.gyms.GymTrainSheetViewModel = hiltViewModel()
    deleteFor?.let { e ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(stringResource(R.string.gym_board_delete_title)) },
            text = { Text(stringResource(R.string.gym_board_delete_body)) },
            confirmButton = { androidx.compose.material3.TextButton({ deleteFor = null; boardSheetVm.deleteEntry(e) { viewModel.load(uid, myUid) } }) { Text(stringResource(R.string.gym_board_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { androidx.compose.material3.TextButton({ deleteFor = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    editFor?.let { e ->
        val day = remember(e) { kotlinx.datetime.LocalDate.parse(e.date) }
        val label = remember(e) {
            java.time.LocalDate.of(day.year, day.monthNumber, day.dayOfMonth).format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", java.util.Locale.getDefault()))
        }
        com.meteomontana.android.ui.screens.gyms.SoloBoardSheet(label, day, { editFor = null; viewModel.load(uid, myUid) }, e.board, edit = e, vm = boardSheetVm)
    }
    addFor?.let { k ->
        val now = remember { java.time.LocalDate.now() }
        com.meteomontana.android.ui.screens.gyms.SoloBoardSheet(
            now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", java.util.Locale.getDefault())),
            kotlinx.datetime.LocalDate(now.year, now.monthValue, now.dayOfMonth), { addFor = null; viewModel.load(uid, myUid) }, k
        )
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meteomontana.android.feature.chat.R.string.common_back)) }
            Text(
                stringResource(R.string.gym_boards_title, GymBreakdown.boardCount(items)), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (items.isEmpty() && onAddBoard == null) Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.gym_boards_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val selected = grades.toSet()
                if (items.isNotEmpty()) GradeChipRow(
                    AscentEntries.grades(items), selected,
                    { g -> grades = if (g in selected) grades - g else grades + g }, { grades = emptyList() }
                )
                if (selected.isNotEmpty()) GradeEntriesList(AscentEntries.entries(s, AscentFilter.BOARDS, selected), onOpenGym)
                else BoardsSections(s, items, state.expandedBoards, viewModel::toggleBoard, onOpenGym, onAddBoard, if (own) { e -> editFor = e } else null, if (own) { e -> deleteFor = e } else null)
                Box(Modifier.padding(bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current + 16.dp))
            }
        }
    }
}
