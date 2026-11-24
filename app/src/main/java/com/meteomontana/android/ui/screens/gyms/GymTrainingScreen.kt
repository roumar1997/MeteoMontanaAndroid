package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentKeys
import com.meteomontana.android.domain.model.gym.GymAscentStats
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymTraining
import com.meteomontana.android.domain.model.gym.GymTrainingPeriod
import com.meteomontana.android.ui.components.LocalTabBarInset
import com.meteomontana.android.ui.components.rememberUserLocation
import com.meteomontana.android.ui.screens.meetups.MeetupGymPickerViewModel
import com.meteomontana.android.ui.screens.meetups.StepperButton
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor
import java.time.format.TextStyle
import java.util.Locale

internal fun parseHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF9AA0A6))

/** Pantalla «Rocódromo» (entrenos) del perfil: Semana · Mes · Año, KPIs, sesiones y lo hecho por color o grado. */
@Composable
fun GymTrainingScreen(onBack: () -> Unit, viewModel: GymTrainingViewModel = hiltViewModel()) {
    val st by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<Pair<Gym, kotlinx.datetime.LocalDate>?>(null) }
    val sessions = st.summary.sessions
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back)) }
            Text(
                stringResource(R.string.gym_train_title), fontFamily = Serif, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 4.dp, bottom = LocalTabBarInset.current + Spacing.lg
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val labels = listOf(R.string.gym_train_week, R.string.gym_train_month, R.string.gym_train_year)
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), CircleShape).padding(4.dp)
                ) {
                    GymTrainingPeriod.entries.forEachIndexed { i, p ->
                        val on = st.period == p
                        Box(
                            Modifier.weight(1f).clip(CircleShape).background(if (on) terraFillColor() else Color.Transparent, CircleShape)
                                .clickable { viewModel.setPeriod(p) }.padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(labels[i]).uppercase(), style = com.meteomontana.android.ui.theme.EyebrowTextStyle, maxLines = 1,
                                color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi(st.summary.sessions.size, R.string.gym_train_kpi_sessions, Modifier.weight(1f))
                    Kpi(st.summary.gyms, R.string.gym_train_kpi_gyms, Modifier.weight(1f))
                    Kpi(st.summary.ascents, R.string.gym_train_kpi_ascents, Modifier.weight(1f))
                }
            }
            item {
                com.meteomontana.android.ui.screens.gyms.GymActionPill(
                    stringResource(R.string.gym_train_trained_at), { picking = true }, Modifier.fillMaxWidth(), primary = true
                )
            }
            if (sessions.isEmpty()) item {
                Text(stringResource(R.string.gym_train_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            }
            if (st.period == GymTrainingPeriod.YEAR) {
                items(st.perGym, key = { "chart-${it.first}" }) { (id, stats) -> GymChart(st.gyms[id], stats, sessions.count { it.gymId == id }) }
            } else {
                items(sessions, key = { "${it.gymId}-${it.date}" }) { s ->
                    SessionCard(
                        s, st.gyms[s.gymId], st.today == s.date, open = st.openKey == (s.gymId to s.date) || (st.openKey == null && s == sessions.first()),
                        onToggle = { viewModel.toggleOpen(s) }, onAdjust = { key, d -> viewModel.adjust(s, key, d) },
                        onEdit = { st.gyms[s.gymId]?.let { g -> sheetFor = g to s.date } }
                    )
                }
            }
        }
    }
    if (picking) GymPickerSheet(onPick = { gym, day -> picking = false; sheetFor = gym to day }, onDismiss = { picking = false })
    sheetFor?.let { (gym, day) ->
        val label = java.time.LocalDate.of(day.year, day.monthNumber, day.dayOfMonth).format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()))
        GymTrainSheet(gym, label, onDismiss = { sheetFor = null; viewModel.refresh() }, date = day)
    }
}

@Composable
private fun Kpi(value: Int, label: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(label).uppercase(), style = com.meteomontana.android.ui.theme.EyebrowTextStyle, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun dayLabel(s: GymSession, today: Boolean): String {
    val d = java.time.LocalDate.of(s.date.year, s.date.monthNumber, s.date.dayOfMonth)
    val text = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + d.dayOfMonth
    return if (today) text else text.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(
    s: GymSession, gym: Gym?, isToday: Boolean, open: Boolean, onToggle: () -> Unit, onAdjust: (String, Int) -> Unit, onEdit: () -> Unit
) {
    val scale = gym?.scale?.takeIf { it.isColors }
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth()) {
        Text(
            (if (isToday) stringResource(R.string.gym_train_today_label, dayLabel(s, true)) else dayLabel(s, false)).uppercase(),
            style = com.meteomontana.android.ui.theme.EyebrowTextStyle, color = if (isToday) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Column(
            Modifier.fillMaxWidth().clip(shape).background(if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
                .border(if (open) 2.dp else 1.dp, if (open) terraFillColor() else MaterialTheme.colorScheme.outline, shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B1B1B)), contentAlignment = Alignment.Center) {
                    Text((gym?.name ?: "R").take(1).uppercase(), color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(gym?.name ?: stringResource(R.string.gym_train_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                    Text(
                        stringResource(if (scale != null) R.string.gym_train_by_colors else R.string.gym_train_by_grade),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(s.totalAscents.toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text(stringResource(R.string.gym_train_ascents_short), style = com.meteomontana.android.ui.theme.EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (open) {
                if (scale != null) Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    scale.levels.forEach { lv ->
                        CounterRow(com.meteomontana.android.util.CatalogLabels.holdColor(lv.name), parseHex(lv.hex), GymTraining.countOf(s.ascents, lv.name), { onAdjust(lv.name, -1) }, { onAdjust(lv.name, 1) })
                    }
                } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    gradeKeys(s).forEach { g ->
                        GradeCounter(g, GymTraining.countOf(s.ascents, g), { onAdjust(g, -1) }, { onAdjust(g, 1) })
                    }
                }
                RouteBoardPills(s.ascents)
                if (s.ascents.isEmpty()) Text(stringResource(R.string.gym_train_log_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.gym_train_edit_day), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = terraFillColor(),
                    modifier = Modifier.align(Alignment.End).clickable(onClick = onEdit).padding(vertical = 4.dp)
                )
            } else if (s.ascents.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GymAscentKeys.blocks(s.ascents).forEach { a -> MiniPill(a, scale?.levelNamed(a.key)?.hex) }
                    RouteBoardPills(s.ascents)
                }
            }
        }
    }
}

/** Grados que se ofrecen: la escalera de siempre más los ya apuntados en esa sesión que no estén en ella. */
private fun gradeKeys(s: GymSession): List<String> =
    GymTraining.DEFAULT_GRADES + GymAscentKeys.blocks(s.ascents).map { it.key }.filter { it !in GymTraining.DEFAULT_GRADES }

@Composable
private fun CounterRow(name: String, color: Color, count: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val shape = CircleShape
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.background, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Text(name.uppercase(), style = com.meteomontana.android.ui.theme.EyebrowTextStyle, modifier = Modifier.weight(1f), maxLines = 1)
        StepperButton("−", if (count > 0) onMinus else null, primary = false, description = "−", size = 34)
        Text(count.toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 6.dp))
        StepperButton("+", onPlus, primary = true, description = "+", size = 34)
    }
}

@Composable
private fun GradeCounter(grade: String, count: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape).background(MaterialTheme.colorScheme.background, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(grade.uppercase(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StepperButton("−", if (count > 0) onMinus else null, primary = false, description = "−", size = 28)
            Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 2.dp))
            StepperButton("+", onPlus, primary = true, description = "+", size = 28)
        }
    }
}

@Composable
private fun MiniPill(a: GymAscent, hex: String?) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.background, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (hex != null) Box(Modifier.size(10.dp).clip(CircleShape).background(parseHex(hex)))
        else Text(com.meteomontana.android.util.CatalogLabels.holdColor(a.key).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(a.count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Barras de «Año»: por color (con la escala del rocódromo) o por grado, con `gym-ascents`. */
@Composable
private fun GymChart(gym: Gym?, stats: GymAscentStats, visits: Int) {
    val scale = gym?.scale?.takeIf { it.isColors }
    val shape = RoundedCornerShape(20.dp)
    val blocks = GymAscentKeys.blocks(stats.items)
    val max = (blocks.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val items = if (scale != null) scale.levels.map { lv -> GymAscent(lv.name, blocks.firstOrNull { it.key.equals(lv.name, true) }?.count ?: 0) } else blocks.sortedBy { it.key }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (scale != null) R.string.gym_train_chart_color else R.string.gym_train_chart_grade, gym?.name ?: "").uppercase(),
                style = com.meteomontana.android.ui.theme.EyebrowTextStyle, color = terraFillColor(), modifier = Modifier.weight(1f)
            )
            Text(pluralStringResource(R.plurals.gym_train_times, visits, visits), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(com.meteomontana.android.util.CatalogLabels.holdColor(a.key).uppercase(), style = com.meteomontana.android.ui.theme.EyebrowTextStyle, modifier = Modifier.width(84.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))) {
                    if (a.count > 0) Box(
                        Modifier.fillMaxWidth(a.count.toFloat() / max).height(14.dp).clip(CircleShape)
                            .background(scale?.levelNamed(a.key)?.hex?.let { parseHex(it) } ?: terraFillColor())
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                    )
                }
                Text(a.count.toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(34.dp))
            }
        }
        RouteBoardPills(stats.items)
    }
}
