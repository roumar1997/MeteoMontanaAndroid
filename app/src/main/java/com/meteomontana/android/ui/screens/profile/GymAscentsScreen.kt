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
import com.meteomontana.android.domain.model.gym.AscentFilter
import com.meteomontana.android.domain.model.gym.GymBreakdown
import com.meteomontana.android.ui.screens.schools.TabButton
import com.meteomontana.android.ui.theme.Serif

/**
 * «Ascensiones · N» (maqueta 4C), propia o de otra persona: pestañas Todo · Bloques · Vías · Boards y, debajo, por
 * rocódromo (solo en «Todo»: el desglose por rocódromo no distingue bloques de vías), por grado y por mes.
 */
@Composable
fun GymAscentsScreen(
    uid: String?,
    onBack: () -> Unit,
    onOpenGym: (Long) -> Unit = {},
    viewModel: ProfileClimbViewModel = hiltViewModel()
) {
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(uid) { viewModel.load(uid, myUid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(AscentFilter.ALL) }
    val s = state.summary
    val items = GymBreakdown.filter(s.items, filter)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back)) }
            Text(
                stringResource(R.string.gym_asc_title, s.ascents), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(3.dp)) {
                AscentFilter.entries.forEach { f ->
                    TabButton(
                        stringResource(when (f) {
                            AscentFilter.ALL -> R.string.gym_asc_tab_all; AscentFilter.BLOCKS -> R.string.gym_asc_tab_blocks
                            AscentFilter.ROUTES -> R.string.gym_asc_tab_routes; AscentFilter.BOARDS -> R.string.gym_asc_tab_boards
                        }).uppercase(),
                        filter == f, Modifier.weight(1f)
                    ) { filter = f }
                }
            }
            if (s.isEmpty) Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.gym_asc_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (filter == AscentFilter.ALL) ByGymSection(s, onOpenGym)
                ByGradeSection(items, viewModel::colorHex)
                if (filter == AscentFilter.ALL) ByMonthSection(s)
                Box(Modifier.padding(bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current + 16.dp))
            }
        }
    }
}
