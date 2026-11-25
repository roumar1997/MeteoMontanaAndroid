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
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.meteomontana.android.ui.screens.schools.TabButton
import com.meteomontana.android.ui.theme.Serif

private enum class HubTab { ALL, ROCK, GYM }

/**
 * Estadísticas con pestañas Todo · Roca · Rocódromo (propias y de otra persona). Roca es la pantalla de siempre
 * (pirámide, objetivo, logros); Rocódromo, sus sesiones, rocódromos, ascensiones, grado máximo y desgloses; Todo junta los
 * totales de las dos cosas y enlaza a la lista de escuelas y a los proyectos.
 */
@Composable
fun StatsHubScreen(
    uid: String?,
    onBack: () -> Unit,
    /** "rock" o "gym": pestaña con la que se abre (las mitades Roca y Rocódromo del perfil); null = Todo. */
    initialTab: String? = null,
    onOpenAscents: () -> Unit = {},
    onOpenSchool: (String) -> Unit = {},
    onOpenBlock: (String, String, String?) -> Unit = { _, _, _ -> },
    onOpenAllSchools: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenGym: (Long) -> Unit = {},
    climb: ProfileClimbViewModel = hiltViewModel()
) {
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(uid) { climb.load(uid, myUid) }
    val state by climb.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable(initialTab) { mutableStateOf(when (initialTab) { "rock" -> HubTab.ROCK; "gym" -> HubTab.GYM; else -> HubTab.ALL }) }
    val gymsOn = com.meteomontana.android.ui.components.LocalFeatureFlags.current.isGymsEnabled
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meteomontana.android.feature.chat.R.string.common_back)) }
            Text(stringResource(R.string.stats_hub_title), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        if (gymsOn) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(3.dp)
        ) {
            TabButton(stringResource(R.string.stats_hub_tab_all).uppercase(), tab == HubTab.ALL, Modifier.weight(1f)) { tab = HubTab.ALL }
            TabButton(stringResource(R.string.stats_hub_tab_rock).uppercase(), tab == HubTab.ROCK, Modifier.weight(1f)) { tab = HubTab.ROCK }
            TabButton(stringResource(R.string.stats_hub_tab_gym).uppercase(), tab == HubTab.GYM, Modifier.weight(1f)) { tab = HubTab.GYM }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (if (gymsOn) tab else HubTab.ROCK) {
                HubTab.ROCK -> StatsScreen(onBack = onBack, onOpenSchool = onOpenSchool, onOpenBlock = onOpenBlock, embedded = true)
                HubTab.GYM -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    GymStatsTab(state, climb, onOpenGym, onOpenAscents)
                }
                HubTab.ALL -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ClimbGradientCard(state, state.rockMax, onStats = {}, onSessions = { tab = HubTab.GYM }, onGyms = { tab = HubTab.GYM }, onAscents = { tab = HubTab.GYM })
                    if (uid == null || uid == myUid) {
                        ProfileLinkRow(androidx.compose.material.icons.Icons.Outlined.Landscape, com.meteomontana.android.ui.theme.Ok, stringResource(R.string.stats_hub_open_schools), state.schools.takeIf { it > 0 }, onOpenAllSchools)
                        ProfileLinkRow(androidx.compose.material.icons.Icons.Outlined.Flag, com.meteomontana.android.ui.theme.terraFillColor(), stringResource(R.string.stats_hub_open_projects), null, onOpenProjects)
                    }
                    Box(Modifier.padding(bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current))
                }
            }
        }
    }
}

/** Pestaña «Rocódromo»: por rocódromo, por grado y por mes; vacía si aún no hay entrenos. */
@Composable
private fun GymStatsTab(state: ProfileClimbState, climb: ProfileClimbViewModel, onOpenGym: (Long) -> Unit, onOpenAscents: () -> Unit) {
    val s = state.summary
    if (s.isEmpty) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.stats_hub_gym_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ClimbGradientCard(state, state.rockMax, onStats = {}, onSessions = {}, onGyms = {}, onAscents = {})
        ProfileLinkRow(androidx.compose.material.icons.Icons.Outlined.Sort, com.meteomontana.android.ui.theme.RainBlue, stringResource(R.string.gym_asc_see_all), s.ascents, onOpenAscents)
        ByGymSection(s, onOpenGym)
        ByGradeSection(s.items, climb::colorHex)
        ByMonthSection(s)
        Box(Modifier.padding(bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current + 16.dp))
    }
}
