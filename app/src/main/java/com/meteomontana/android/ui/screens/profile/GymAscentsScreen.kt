package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.meteomontana.android.ui.screens.schools.TabButton
import com.meteomontana.android.ui.theme.Serif

/**
 * «Ascensiones · N» (maqueta 4C), propia o de otra persona: pestañas Todo · Bloques · Vías · Boards. Cada rocódromo tiene
 * su escala (colores o grados), así que Todo, Bloques y Vías van agrupados POR ROCÓDROMO con su propio reparto; Boards va
 * agrupado por tablero (Kilter, MoonBoard...) y luego por grado. Tocar un rocódromo abre lo que se hizo allí.
 */
@Composable
fun GymAscentsScreen(
    uid: String?,
    onBack: () -> Unit,
    onOpenGym: (Long) -> Unit = {},
    onOpenBoards: () -> Unit = {},
    viewModel: ProfileClimbViewModel = hiltViewModel()
) {
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(uid) { viewModel.load(uid, myUid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(AscentFilter.ALL) }
    val own = uid == null || uid == myUid
    // Propio: el que elegi (movil + servidor). Ajeno: el que eligio esa persona (viene en su resumen).
    val order = com.meteomontana.android.domain.model.gym.AscentTabOrder.parse(state.ascentOrder)
    var reordering by remember { mutableStateOf(false) }
    if (reordering && own) AscentOrderDialog(
        order, state.summary.byGym,
        onChange = { viewModel.setAscentOrder(com.meteomontana.android.domain.model.gym.AscentTabOrder.serialize(it)) },
        onChangeGyms = viewModel::setGymOrder, onClose = { reordering = false }
    )
    val s = state.summary
    // «Todo» sin boards: no se reparten por rocodromo (van juntos en la pantalla Boards).
    val items = if (filter == AscentFilter.ALL) GymBreakdown.withoutBoards(s.items) else GymBreakdown.filter(s.items, filter)
    // Grados elegidos en los chips (varios a la vez); al cambiar de pestana se empieza de nuevo.
    var grades by remember(filter) { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meteomontana.android.feature.chat.R.string.common_back)) }
            Text(
                stringResource(R.string.gym_asc_title, s.ascents), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
            )
            if (own) OrderPill { reordering = true }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(3.dp)) {
                com.meteomontana.android.domain.model.gym.AscentTabOrder.tabs(order).forEach { f ->
                    TabButton(
                        ascentFilterLabel(f).uppercase(),
                        filter == f, Modifier.weight(1f)
                    ) { filter = f }
                }
            }
            if (s.isEmpty) Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.gym_asc_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GradeChipRow(AscentEntries.grades(items), grades, { g -> grades = if (g in grades) grades - g else grades + g }, { grades = emptySet() })
                if (grades.isNotEmpty()) GradeEntriesList(AscentEntries.entries(s, filter, grades, includeBoards = false), onOpenGym)
                else AscentsBody(s, filter, items, viewModel::colorHex, viewModel::colorHexFor, onOpenGym, state.expandedBoards, viewModel::toggleBoard, onOpenBoards)
                Box(Modifier.padding(bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current + 16.dp))
            }
        }
    }
}
