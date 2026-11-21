package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.GymBreakdown
import com.meteomontana.android.domain.model.gym.GymSummaryGym
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.terraFillColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lo que una persona ha hecho EN un rocódromo (propia o ajena, con la privacidad del resumen): totales, el reparto con
 * la escala de ese rocódromo y sus días de entreno. La ficha del rocódromo queda como acción secundaria.
 */
@Composable
fun GymVisitScreen(
    uid: String?,
    gymId: Long,
    onBack: () -> Unit,
    onOpenGym: (Long) -> Unit,
    viewModel: ProfileClimbViewModel = hiltViewModel()
) {
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(uid) { viewModel.load(uid, myUid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val g = state.summary.byGym.firstOrNull { it.gymId == gymId }
    val title = g?.name ?: viewModel.gym(gymId)?.name ?: "—"
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back)) }
            Text(
                title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 2,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (g == null) Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.gym_visit_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            } else VisitBody(GymBreakdown.visitWithoutBoards(g), { key -> viewModel.colorHexFor(gymId, key) }, onOpenGym)
            Box(Modifier.padding(bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current + 16.dp))
        }
    }
}

@Composable
private fun VisitBody(g: GymSummaryGym, colorHex: (String) -> String?, onOpenGym: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VisitCell(g.sessions.toString(), stringResource(R.string.gym_visit_sessions), Modifier.weight(1f))
        VisitCell(g.ascents.toString(), stringResource(R.string.gym_visit_ascents), Modifier.weight(1f))
        VisitCell(g.maxGrade?.uppercase() ?: "—", stringResource(R.string.gym_visit_max), Modifier.weight(1f))
    }
    // Acción secundaria: la ficha del rocódromo.
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).border(1.dp, terraFillColor(), CircleShape).clickable { onOpenGym(g.gymId) }.padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.gym_visit_open_gym), color = terraFillColor(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
    ByGradeSection(g.items, colorHex)
    if (g.days.isNotEmpty()) {
        SectionTitle(stringResource(R.string.gym_visit_days))
        CardBox {
            g.days.forEach { d ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(d.date), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    Text(
                        if (d.ascents > 0) pluralStringResource(R.plurals.gym_visit_day_count, d.ascents, d.ascents) else stringResource(R.string.gym_visit_day_none),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VisitCell(value: String, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
        Text(label.uppercase(), fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** «mié 18 nov 2026» en el idioma del móvil; si la fecha no se entiende, el texto tal cual. */
internal fun dayLabel(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())) }.getOrDefault(iso)
