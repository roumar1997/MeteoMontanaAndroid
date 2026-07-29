package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.domain.usecase.journal.JournalStatsCalculator
import com.meteomontana.android.ui.components.VotableChip
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra

/**
 * MIS ESTADÍSTICAS (C4): pirámide, racha, progresión — todo calculado en
 * shared (JournalStatsCalculator) a partir del diario. El selector de año es
 * DESPLEGABLE (Todo / 2026 / 2025…) con vista por meses opcional; mismo
 * lenguaje visual pulsable que la orientación y el grado (chip discontinuo).
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    /** N7: abrir el diario de una escuela concreta (vista de sectores). */
    onOpenSchool: (String) -> Unit = {},
    /** Ir a la piedra/vía en su escuela: (schoolId, lineName, lineId). */
    onOpenBlock: (String, String, String?) -> Unit = { _, _, _ -> },
    viewModel: StatsViewModel = hiltViewModel()
) {
    var showDaysList by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var yearMenuOpen by remember { mutableStateOf(false) }
    // Fila de la pirámide tocada → diálogo con las vías de ese grado.
    var gradeDetail by remember { mutableStateOf<String?>(null) }
    // Escuela desplegada inline en TUS ESCUELAS (null = ninguna).
    var expandedSchool by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)
    ) {
        item {
            val shareCtx = androidx.compose.ui.platform.LocalContext.current
            val shareScope = androidx.compose.runtime.rememberCoroutineScope()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = Spacing.sm))
                Text("MIS ESTADÍSTICAS", style = EyebrowTextStyle, color = Terra)
                Spacer(Modifier.weight(1f))
                // C6: compartir como imagen (formato historia, estilo Wrapped).
                state.summary?.let { sum ->
                    androidx.compose.material3.Icon(
                        Icons.Outlined.Share,
                        contentDescription = "Compartir estadísticas",
                        tint = Terra,
                        modifier = Modifier.clickable {
                            shareScope.launch {
                                com.meteomontana.android.ui.share.shareStatsAsImage(
                                    context = shareCtx,
                                    periodLabel = state.year?.let { y -> "MI $y EN ROCA" }
                                        ?: "MI DIARIO EN ROCA",
                                    disciplineLabel = if (state.discipline == "ROUTE") "VÍA" else "BLOQUE",
                                    summary = sum,
                                    maxGrade = sum.pyramid.firstOrNull()?.first,
                                    progression = state.progression
                                )
                            }
                        }.padding(Spacing.xs)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))

            // ── Filtros: disciplina + año desplegable + mes ──────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically) {
                FilterChip("BLOQUE", state.discipline == "BOULDER") { viewModel.setDiscipline("BOULDER") }
                FilterChip("VÍA", state.discipline == "ROUTE") { viewModel.setDiscipline("ROUTE") }
                Box {
                    VotableChip(text = state.year ?: "TODO") { yearMenuOpen = true }
                    DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Todo") },
                            onClick = { viewModel.setYear(null); yearMenuOpen = false })
                        state.availableYears.forEach { y ->
                            DropdownMenuItem(text = { Text(y) },
                                onClick = { viewModel.setYear(y); yearMenuOpen = false })
                        }
                    }
                }
            }
            // Meses del año elegido (solo con año concreto).
            if (state.year != null) {
                Spacer(Modifier.height(Spacing.sm))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item { FilterChip("AÑO ENTERO", state.month == null) { viewModel.setMonth(null) } }
                    items(12) { i ->
                        val m = (i + 1).toString().padStart(2, '0')
                        FilterChip(MONTHS[i], state.month == m) { viewModel.setMonth(m) }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))

            // ── Métricas ─────────────────────────────────────────────────────
            val s = state.summary
            if (s != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricCard("DÍAS DE ROCA ▾", s.daysOut.toString(),
                        Modifier.weight(1f).clickable { showDaysList = true })
                    MetricCard("RACHA", "${s.currentStreakWeeks} sem", Modifier.weight(1f), terra = true)
                }
                if (showDaysList) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDaysList = false },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { showDaysList = false }) {
                                Text("CERRAR", style = EyebrowTextStyle, color = Terra)
                            }
                        },
                        title = { Text("Tus días de roca", fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold) },
                        text = {
                            val days = remember { viewModel.daysWithCounts() }
                            LazyColumn(Modifier.height(360.dp)) {
                                items(days.size) { i ->
                                    val (day, count) = days[i]
                                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(day, style = MaterialTheme.typography.bodyMedium)
                                        Text("$count ascensos",
                                            style = EyebrowTextStyle.copy(fontSize = 10.sp),
                                            color = Terra)
                                    }
                                }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricCard("PROYECTOS CAÍDOS", s.projectsFallen.toString(), Modifier.weight(1f))
                    MetricCard("MEDIA/DÍA", s.avgPerDay.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.lg))

                // ── Pirámide ────────────────────────────────────────────────
                Text("PIRÁMIDE DE GRADOS", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                val maxCount = s.pyramid.maxOfOrNull { it.second } ?: 1
                s.pyramid.take(10).forEachIndexed { i, (grade, count) ->
                    Row(Modifier
                        .clickable { gradeDetail = grade }
                        .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(grade, style = EyebrowTextStyle.copy(fontSize = 11.sp),
                            color = if (i == 0) Terra else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(36.dp))
                        Box(Modifier.weight(1f)) {
                            Box(Modifier
                                .fillMaxWidth(count / maxCount.toFloat())
                                .height(14.dp)
                                .background(Terra.copy(alpha = 1f - i * 0.08f),
                                    RoundedCornerShape(4.dp)))
                        }
                        Text(count.toString(), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Detalle de un grado: qué vías de ese grado llevas (pulsables).
                gradeDetail?.let { grade ->
                    val gradeEntries = remember(grade, state) { viewModel.entriesForGrade(grade) }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { gradeDetail = null },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { gradeDetail = null }) {
                                Text("CERRAR", style = EyebrowTextStyle, color = Terra)
                            }
                        },
                        title = { Text("Tus ${grade.uppercase()}", fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold) },
                        text = {
                            LazyColumn(Modifier.height(360.dp)) {
                                items(gradeEntries.size) { i ->
                                    val e = gradeEntries[i]
                                    Column(Modifier.fillMaxWidth()
                                        .clickable(enabled = e.schoolId != null) {
                                            e.schoolId?.let { sid ->
                                                gradeDetail = null
                                                onOpenBlock(sid, e.blockName, e.lineId)
                                            }
                                        }
                                        .padding(vertical = 8.dp)) {
                                        Text(e.blockName, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text((e.schoolName ?: "—") +
                                            (if (e.schoolId != null) "  ·  VER ▸" else ""),
                                            style = EyebrowTextStyle.copy(fontSize = 9.sp),
                                            color = Terra)
                                    }
                                }
                            }
                        }
                    )
                }
                s.bestMonth?.let { bm ->
                    Spacer(Modifier.height(Spacing.sm))
                    // N7: pulsable — filtra las estadisticas a ESE mes.
                    Box(Modifier.clickable {
                        viewModel.setYear(bm.take(4)); viewModel.setMonth(bm.substringAfter('-'))
                    }) {
                        InfoCard("Tu mejor mes: ${formatMonth(bm)} (${s.bestMonthCount} ascensos). Toca para verlo ▾")
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            // ── Progresión ──────────────────────────────────────────────────
            val p = state.progression
            if (p != null) {
                Text("ASCENSOS POR MES · ÚLT. 12", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                MonthBars(p.monthlyCounts)
                Spacer(Modifier.height(Spacing.lg))

                if (p.maxGradePerQuarter.isNotEmpty()) {
                    Text("GRADO MÁXIMO POR TRIMESTRE", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xs))
                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        p.maxGradePerQuarter.takeLast(6).forEach { (q, g) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.width(10.dp).height(10.dp)
                                    .background(Terra, CircleShape))
                                Text(g, fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(q.substringAfter('-'), fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }

                Text("ÚLTIMAS 12 SEMANAS", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    p.weeksOut.forEach { out ->
                        Box(Modifier.weight(1f).height(18.dp)
                            .background(
                                if (out) Terra else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp)))
                    }
                }
                Text("Cada casilla = 1 semana · terra = saliste", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp))
                Spacer(Modifier.height(Spacing.lg))

                Text("TUS ESCUELAS", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                p.perSchool.take(8).forEach { (school, count, maxGrade) ->
                    val isOpen = expandedSchool == school
                    Row(Modifier.fillMaxWidth()
                        .clickable { expandedSchool = if (isOpen) null else school }
                        .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(school, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("$count" + (maxGrade?.let { " · máx $it" } ?: "") +
                            (if (isOpen) " ▴" else " ▾"),
                            style = EyebrowTextStyle.copy(fontSize = 11.sp), color = Terra)
                    }
                    // Desplegado inline: los nombres de las vías de esa escuela,
                    // pulsables → abren la piedra en su escuela.
                    if (isOpen) {
                        val schoolEntries = remember(school, state) { viewModel.entriesForSchool(school) }
                        Column(Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = Spacing.sm, vertical = 4.dp)) {
                            schoolEntries.take(30).forEach { e ->
                                Row(Modifier.fillMaxWidth()
                                    .clickable(enabled = e.schoolId != null) {
                                        e.schoolId?.let { sid -> onOpenBlock(sid, e.blockName, e.lineId) }
                                    }
                                    .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(e.blockName, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text((e.grade ?: "—") + (if (e.schoolId != null) " ▸" else ""),
                                        style = EyebrowTextStyle.copy(fontSize = 10.sp), color = Terra)
                                }
                            }
                            Text("VER EN EL DIARIO ▸", style = EyebrowTextStyle.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { onOpenSchool(school) }
                                    .padding(vertical = 6.dp))
                        }
                        Spacer(Modifier.height(Spacing.xs))
                    }
                }
            }

            if (state.summary == null && !state.loading) {
                InfoCard("Marca vías como hechas y aquí verás tu pirámide, tu racha y tu progresión.")
            }
        }
    }
}

private val MONTHS = listOf("ENE","FEB","MAR","ABR","MAY","JUN","JUL","AGO","SEP","OCT","NOV","DIC")

private fun formatMonth(yyyyMm: String): String = runCatching {
    MONTHS[yyyyMm.substringAfter('-').toInt() - 1].lowercase() + " " + yyyyMm.take(4)
}.getOrDefault(yyyyMm)

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier
        .background(if (selected) Terra else MaterialTheme.colorScheme.surface,
            RoundedCornerShape(6.dp))
        .border(1.dp, if (selected) Terra else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(6.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, style = EyebrowTextStyle.copy(fontSize = 10.sp),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, terra: Boolean = false) {
    Column(modifier
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, color = if (terra) Terra else MaterialTheme.colorScheme.onSurface)
        Text(label, style = EyebrowTextStyle.copy(fontSize = 8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(text: String) {
    Box(Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        .padding(Spacing.sm)) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MonthBars(counts: List<Pair<String, Int>>) {
    val max = (counts.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(90.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom) {
        counts.forEach { (month, count) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (count > 0) Text(count.toString(), fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth()
                    .height((70 * count / max).coerceAtLeast(if (count > 0) 4 else 1).dp)
                    .background(Terra.copy(alpha = 0.4f + 0.6f * count / max),
                        RoundedCornerShape(3.dp)))
                Text(MONTHS[month.substringAfter('-').toInt() - 1].take(1), fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
