package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.AscentKey
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymAscentKeys
import com.meteomontana.android.domain.model.gym.GymStats
import com.meteomontana.android.domain.model.gym.GymTrainingPeriod
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymStatsUseCase
import com.meteomontana.android.domain.usecase.gym.GymTrainingUseCase
import com.meteomontana.android.ui.screens.gyms.GymPickerSheet
import com.meteomontana.android.ui.screens.gyms.GymTrainSheet
import com.meteomontana.android.ui.screens.gyms.parseHex
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.gradeChipStyle
import com.meteomontana.android.ui.theme.terraFillColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.util.Locale
import javax.inject.Inject

/** Un tramo de la barra de colores del mes: un grado o el color de una escala, con cuántas veces y su orden de dificultad. */
data class BarSeg(val grade: String?, val hex: String?, val label: String, val count: Int, val order: Int)

data class LastTraining(val date: LocalDate, val gymName: String, val total: Int)

data class GymStatsUiState(
    val stats: GymStats = GymStats(),
    val topGymName: String? = null,
    val topGymId: Long? = null,
    val monthSessions: Int = 0,
    val monthBlocks: Int = 0,
    val monthRoutes: Int = 0,
    val segments: List<BarSeg> = emptyList(),
    val last: LastTraining? = null
)

/**
 * Tarjeta «Rocódromo» del perfil. Los números salen de `shared` ([GetGymStatsUseCase], [GymTrainingUseCase]): primero lo
 * que hay en el móvil (sale al instante y sin red) y después, si el servidor ya tiene las rutas de entrenos, lo mezcla.
 */
@HiltViewModel
class GymStatsViewModel @Inject constructor(
    private val getStats: GetGymStatsUseCase,
    private val getCachedGyms: GetCachedGymsUseCase,
    private val training: GymTrainingUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(build(getStats.cached()))
    val state: StateFlow<GymStatsUiState> = _state

    fun refresh() {
        _state.value = build(getStats.cached())
        viewModelScope.launch { runCatching { getStats.refresh() }.onSuccess { s -> _state.update { build(s) } } }
    }

    fun gym(id: Long): Gym? = getCachedGyms().firstOrNull { it.id == id }

    private fun build(stats: GymStats): GymStatsUiState {
        val gyms = getCachedGyms().associateBy { it.id }
        val today = training.today()
        val month = training.summary(GymTrainingPeriod.MONTH, today)
        val counts = LinkedHashMap<String, BarSeg>()
        var blocks = 0
        var routes = 0
        month.sessions.forEach { s ->
            val scale = gyms[s.gymId]?.scale?.takeIf { it.isColors }
            s.ascents.forEach { a ->
                val seg: BarSeg = when (val k = GymAscentKeys.parse(a.key)) {
                    is AscentKey.Route -> { routes += a.count; gradeSeg(k.grade, a.count) }
                    is AscentKey.Board -> { blocks += a.count; gradeSeg(k.grade, a.count) }
                    is AscentKey.Block -> {
                        blocks += a.count
                        val lv = scale?.levelNamed(a.key)
                        if (lv != null) BarSeg(null, lv.hex, lv.name.replaceFirstChar { it.uppercase() }, a.count, 1000 + scale.levels.indexOf(lv))
                        else gradeSeg(a.key, a.count)
                    }
                }
                val id = seg.hex ?: ("g" + seg.grade)
                counts[id] = counts[id]?.let { it.copy(count = it.count + seg.count) } ?: seg
            }
        }
        val lastSession = training.summary(GymTrainingPeriod.YEAR, today).sessions.sortedBy { it.date }.lastOrNull()
        return GymStatsUiState(
            stats = stats,
            topGymName = stats.topGymId?.let { gyms[it]?.name },
            topGymId = stats.topGymId,
            monthSessions = stats.month,
            monthBlocks = blocks,
            monthRoutes = routes,
            segments = counts.values.sortedBy { it.order },
            last = lastSession?.let { LastTraining(it.date, gyms[it.gymId]?.name ?: "", it.totalAscents) }
        )
    }

    /** Un grado doble (6b+/6c) cuenta con su grado base: color, orden y barra. */
    private fun gradeSeg(grade: String, count: Int) = GymAscentKeys.baseGrade(grade).let { BarSeg(it, null, it.uppercase(), count, gradeOrder(it)) }

    /** Orden de dificultad de un grado francés: 6a < 6a+ < 6b … (número, letra, plus). */
    private fun gradeOrder(grade: String): Int {
        val m = Regex("^([3-9])([abc])?(\\+)?$").matchEntire(grade.lowercase()) ?: return 0
        return m.groupValues[1].toInt() * 100 + (m.groupValues[2].ifEmpty { "a" }[0] - 'a') * 10 + if (m.groupValues[3] == "+") 1 else 0
    }
}

@Composable
private fun segColor(s: BarSeg): Color = s.hex?.let { parseHex(it) } ?: gradeChipStyle(s.grade).stroke

@Composable
internal fun GymStatsCard(modifier: Modifier = Modifier, onClick: () -> Unit = {}, onOpenGym: (Long) -> Unit = {}, visible: Boolean = true, viewModel: GymStatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Perfil es una pestaña que se queda viva: sin la clave `visible`, la tarjeta se quedaba con lo del primer arranque
    // y «He entrenado aquí hoy» no se veía al volver al perfil.
    LaunchedEffect(visible) { if (visible) viewModel.refresh() }
    var picking by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<Pair<Gym, LocalDate>?>(null) }
    val s = state.stats
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s.isEmpty) {
            Text(stringResource(R.string.gym_stats_title).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.gym_stats_empty), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MonthCard(state, onClick)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val gymId = state.topGymId
                InfoCell(
                    stringResource(R.string.gym_stats_top), Modifier.weight(1f).let { if (gymId != null) it.clickable { onOpenGym(gymId) } else it }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.topGymName?.let { name ->
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(terraFillColor()), contentAlignment = Alignment.Center) {
                                Text(name.take(1).uppercase(), color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, modifier = Modifier.weight(1f))
                        }
                    }
                    Text(pluralStringResource(R.plurals.gym_stats_visits, s.topGymVisits, s.topGymVisits), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                InfoCell(stringResource(R.string.gym_stats_streak), Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.LocalFireDepartment, null, tint = terraFillColor(), modifier = Modifier.size(24.dp))
                        Text(s.streakWeeks.toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    }
                    Text(stringResource(R.string.gym_stats_streak_sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.last?.let { l ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(stringResource(R.string.gym_stats_last).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOf(dayText(l.date), l.gymName, pluralStringResource(R.plurals.gym_stats_ascents, l.total, l.total)).filter { it.isNotBlank() }.joinToString(" · "),
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(terraFillColor()).clickable { picking = true }.padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.gym_pick_eyebrow).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
    if (picking) GymPickerSheet(onPick = { gym, day -> picking = false; sheetFor = gym to day }, onDismiss = { picking = false })
    sheetFor?.let { (gym, day) ->
        val label = java.time.LocalDate.of(day.year, day.monthNumber, day.dayOfMonth)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()))
        GymTrainSheet(gym, label, onDismiss = { sheetFor = null; viewModel.refresh() }, date = day)
    }
}

@Composable
private fun dayText(d: LocalDate): String {
    val today = java.time.LocalDate.now()
    val day = java.time.LocalDate.of(d.year, d.monthNumber, d.dayOfMonth)
    return when (java.time.temporal.ChronoUnit.DAYS.between(day, today)) {
        0L -> stringResource(R.string.gym_pick_today)
        1L -> stringResource(R.string.gym_pick_yesterday)
        else -> day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

/** Tarjeta de degradado del mes: sesiones, bloques y vías, y la barra con los colores de lo que has escalado. */
@Composable
private fun MonthCard(state: GymStatsUiState, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val month = java.time.LocalDate.now().month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF7B3FB0), Color(0xFFC2410C), Color(0xFFE88A3A))), shape)
            .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (stringResource(R.string.gym_stats_month) + " · " + month).uppercase(), style = EyebrowTextStyle,
                color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f), maxLines = 1
            )
            Text(
                stringResource(R.string.gym_stats_see_all).uppercase() + " ›", style = EyebrowTextStyle, color = Color(0xFF2A2924), maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
            BigStat(state.monthSessions, R.string.gym_train_kpi_sessions)
            BigStat(state.monthBlocks, R.string.gym_stats_blocks)
            BigStat(state.monthRoutes, R.string.gym_stats_routes)
        }
        if (state.segments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                state.segments.forEach { seg -> Box(Modifier.weight(seg.count.toFloat()).height(8.dp).background(segColor(seg))) }
            }
            state.segments.lastOrNull()?.let {
                Text(stringResource(R.string.gym_stats_top_level, it.label), fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f))
            }
        }
    }
}

@Composable
private fun BigStat(value: Int, label: Int) {
    Column {
        Text(value.toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Color.White)
        Text(stringResource(label), fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
    }
}

@Composable
private fun InfoCell(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surface, shape)
            .then(Modifier.padding(14.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        content()
    }
}
