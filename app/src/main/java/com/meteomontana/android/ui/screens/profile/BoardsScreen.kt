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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back)) }
            Text(
                stringResource(R.string.gym_boards_title, GymBreakdown.boardCount(items)), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (items.isEmpty()) Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.gym_boards_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val selected = grades.toSet()
                GradeChipRow(
                    AscentEntries.grades(items), selected,
                    { g -> grades = if (g in selected) grades - g else grades + g }, { grades = emptyList() }
                )
                if (selected.isNotEmpty()) GradeEntriesList(AscentEntries.entries(s, AscentFilter.BOARDS, selected), onOpenGym)
                else BoardsSections(s, items, state.collapsedBoards, viewModel::toggleBoard, onOpenGym)
                Box(Modifier.padding(bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current + 16.dp))
            }
        }
    }
}
