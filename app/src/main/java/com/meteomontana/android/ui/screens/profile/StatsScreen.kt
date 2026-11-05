package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.theme.terraFillColor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import com.meteomontana.android.ui.components.cumbreSheetSurface
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.EventAvailable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.meteomontana.android.ui.theme.Ok
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
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/**
 * MIS ESTADÍSTICAS (C4): pirámide, racha, progresión — todo calculado en
 * shared (JournalStatsCalculator) a partir del diario. El selector de año es
 * DESPLEGABLE (Todo / 2026 / 2025…) con vista por meses opcional; mismo
 * lenguaje visual pulsable que la orientación y el grado (chip discontinuo).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
                Text(stringResource(R.string.stats_screen_mis_estadisticas), style = EyebrowTextStyle, color = Terra)
                Spacer(Modifier.weight(1f))
                // C6: compartir como imagen (formato historia, estilo Wrapped).
                state.summary?.takeIf { state.isOwn }?.let { sum ->
                    androidx.compose.material3.Icon(
                        Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.stats_screen_compartir_estadisticas),
                        tint = Terra,
                        modifier = Modifier.clickable {
                            shareScope.launch {
                                com.meteomontana.android.ui.share.shareStatsAsImage(
                                    context = shareCtx,
                                    periodLabel = state.year?.let { y -> AppText.get(R.string.stats_screen_v3_mi_en_roca, y) }
                                        ?: AppText.get(R.string.stats_screen_v3_mi_diario_en_roca),
                                    disciplineLabel = if (state.discipline == "ROUTE") AppText.get(R.string.add_block_sheet_v3_via) else "BLOQUE",
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

            // ── Objetivo del año (solo en las tuyas) ─────────────────────────
            if (state.isOwn && state.goalLoaded) {
                var goalSheetOpen by remember { mutableStateOf(false) }
                val goalCtx = androidx.compose.ui.platform.LocalContext.current
                GoalSection(state.goal, state.currentYear, onEdit = { goalSheetOpen = true })
                if (goalSheetOpen) {
                    GoalSheet(
                        year = state.currentYear,
                        initial = viewModel.currentGoal(),
                        onSave = { d, g, a, days ->
                            viewModel.saveGoal(d, g, a, days) { ok ->
                                if (ok) goalSheetOpen = false
                                else android.widget.Toast.makeText(goalCtx, AppText.get(R.string.goal_save_error),
                                    android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRemove = { viewModel.removeGoal(); goalSheetOpen = false },
                        onDismiss = { goalSheetOpen = false }
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }

            // ── Logros: plegados bajo un botón, arriba (de todo el diario, no del filtro) ──
            if (!state.loading) {
                AchievementsSection(state.achievements, state.rarity)
                Spacer(Modifier.height(Spacing.md))
            }

            // ── Filtros: disciplina + año desplegable + mes ──────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically) {
                FilterChip(stringResource(R.string.w_boulder_caps2), state.discipline == "BOULDER") { viewModel.setDiscipline("BOULDER") }
                FilterChip(stringResource(R.string.add_block_sheet_v3_via), state.discipline == "ROUTE") { viewModel.setDiscipline("ROUTE") }
                var gradeMenuOpen by remember { mutableStateOf(false) }
                Box {
                    VotableChip(text = state.grade?.uppercase() ?: stringResource(R.string.w_grade_caps)) { gradeMenuOpen = true }
                    DropdownMenu(expanded = gradeMenuOpen, onDismissRequest = { gradeMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.stats_screen_v2_todos)) },
                            onClick = { viewModel.setGrade(null); gradeMenuOpen = false })
                        state.availableGrades.forEach { g ->
                            DropdownMenuItem(text = { Text(g, color = gradeAccent(g),
                                fontWeight = FontWeight.Bold) },
                                onClick = { viewModel.setGrade(g); gradeMenuOpen = false })
                        }
                    }
                }
                Box {
                    VotableChip(text = state.year ?: stringResource(R.string.w_all_caps2)) { yearMenuOpen = true }
                    DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.stats_screen_v2_todo)) },
                            onClick = { viewModel.setYear(null); yearMenuOpen = false })
                        state.availableYears.forEach { y ->
                            DropdownMenuItem(text = { Text(y) },
                                onClick = { viewModel.setYear(y); yearMenuOpen = false })
                        }
                    }
                }
            }
            // Día concreto activo (desde DÍAS DE ROCA): chip para quitarlo.
            state.day?.let { d ->
                Spacer(Modifier.height(Spacing.sm))
                FilterChip(stringResource(R.string.stats_screen_v3_dia, d), true) { viewModel.setDay(null) }
            }
            // Meses del año elegido (solo con año concreto).
            if (state.year != null) {
                Spacer(Modifier.height(Spacing.sm))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item { FilterChip(stringResource(R.string.stats_screen_v3_ano_entero), state.month == null) { viewModel.setMonth(null) } }
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
                    MetricCard(stringResource(R.string.stats_screen_v3_dias_de_roca), s.daysOut.toString(),
                        Modifier.weight(1f).clickable { showDaysList = true })
                    MetricCard(stringResource(R.string.w_streak_caps), "${s.currentStreakWeeks} sem", Modifier.weight(1f), terra = true)
                }
                if (showDaysList) {
                    // Bottom sheet suave (paridad iOS), no dialog a pantalla
                    // completa — feedback de Rodrigo.
                    androidx.compose.material3.ModalBottomSheet(
                        onDismissRequest = { showDaysList = false },
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        shape = com.meteomontana.android.ui.components.CumbreSheetShape
                    ) {
                        val days = remember { viewModel.daysWithCounts() }
                        // Día pulsable -> se despliega con los ascensos de ese
                        // día (pulsables -> abren su piedra).
                        var expandedDay by remember { mutableStateOf<String?>(null) }
                        Column(Modifier.fillMaxWidth()
                            .cumbreSheetSurface(MaterialTheme.colorScheme.background)
                            .padding(horizontal = Spacing.md)) {
                            Text(stringResource(R.string.stats_screen_tus_dias_de_roca), style = EyebrowTextStyle, color = Terra)
                            Spacer(Modifier.height(Spacing.sm))
                            LazyColumn(Modifier.height(420.dp)) {
                                items(days.size) { i ->
                                    val (day, count) = days[i]
                                    val isOpen = expandedDay == day
                                    Row(Modifier.fillMaxWidth()
                                        .clickable { expandedDay = if (isOpen) null else day }
                                        .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(day, style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text(stringResource(R.string.stats_screen_v2_1_s_ascensos, count) + (if (isOpen) " ▴" else " ▾"),
                                            style = EyebrowTextStyle.copy(fontSize = 10.sp),
                                            color = Terra)
                                    }
                                    if (isOpen) {
                                        // S4: cargar TODA la pantalla con ese día.
                                        Text(stringResource(R.string.stats_screen_ver_estadisticas_de_este),
                                            style = EyebrowTextStyle.copy(fontSize = 9.sp),
                                            color = Terra,
                                            modifier = Modifier
                                                .clickable {
                                                    showDaysList = false
                                                    viewModel.setDay(day)
                                                }
                                                .padding(start = Spacing.md, top = 4.dp, bottom = 6.dp))
                                        viewModel.entriesForDay(day).forEach { e ->
                                            Row(Modifier.fillMaxWidth()
                                                .clickable(enabled = e.schoolId != null) {
                                                    e.schoolId?.let { sid ->
                                                        showDaysList = false
                                                        onOpenBlock(sid, e.blockName, e.lineId)
                                                    }
                                                }
                                                .padding(start = Spacing.md, top = 4.dp, bottom = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(e.blockName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface)
                                                Text((e.grade ?: "—") +
                                                    (if (e.schoolId != null) " ▸" else ""),
                                                    style = EyebrowTextStyle.copy(fontSize = 10.sp),
                                                    color = Terra)
                                            }
                                        }
                                    }
                                    androidx.compose.material3.HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricCard(stringResource(R.string.stats_screen_v3_proyectos_caidos), s.projectsFallen.toString(), Modifier.weight(1f))
                    MetricCard(stringResource(R.string.stats_screen_v3_media_dia), s.avgPerDay.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.sm))
                // % flash / a vista y, con un año elegido, comparado con el anterior.
                StyleAndYearSection(state.styleShare, state.yearComparison)
                Spacer(Modifier.height(Spacing.lg))

                // ── Pirámide ────────────────────────────────────────────────
                Text(stringResource(R.string.stats_screen_piramide_de_grados), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                val maxCount = s.pyramid.maxOfOrNull { it.second } ?: 1
                s.pyramid.take(10).forEachIndexed { i, (grade, count) ->
                    // G: cada barra con el color de SU grado (paleta de topos).
                    val accent = gradeAccent(grade)
                    Row(Modifier
                        .clickable { gradeDetail = grade }
                        .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(grade, style = EyebrowTextStyle.copy(fontSize = 11.sp),
                            color = accent,
                            modifier = Modifier.width(36.dp))
                        Box(Modifier.weight(1f)) {
                            Box(Modifier
                                .fillMaxWidth(count / maxCount.toFloat())
                                .height(14.dp)
                                .background(accent, RoundedCornerShape(4.dp)))
                        }
                        Text(count.toString(), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Detalle de un grado: qué vías de ese grado llevas (pulsables).
                gradeDetail?.let { grade ->
                    val gradeEntries = remember(grade, state) { viewModel.entriesForGrade(grade) }
                    // Bottom sheet suave (paridad iOS), no dialog a pantalla.
                    androidx.compose.material3.ModalBottomSheet(
                        onDismissRequest = { gradeDetail = null },
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        shape = com.meteomontana.android.ui.components.CumbreSheetShape
                    ) {
                        Column(Modifier.fillMaxWidth()
                            .cumbreSheetSurface(MaterialTheme.colorScheme.background)
                            .padding(horizontal = Spacing.md)) {
                            Text(stringResource(R.string.stats_screen_v2_tus_1_s, grade.uppercase()), style = EyebrowTextStyle, color = Terra)
                            Spacer(Modifier.height(Spacing.sm))
                            LazyColumn(Modifier.height(420.dp)) {
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
                                            (if (e.schoolId != null) stringResource(R.string.stats_screen_v3_ver) else ""),
                                            style = EyebrowTextStyle.copy(fontSize = 9.sp),
                                            color = Terra)
                                    }
                                    androidx.compose.material3.HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
                s.bestMonth?.let { bm ->
                    Spacer(Modifier.height(Spacing.sm))
                    // N7: pulsable — filtra las estadisticas a ESE mes.
                    Box(Modifier.clickable {
                        viewModel.setYear(bm.take(4)); viewModel.setMonth(bm.substringAfter('-'))
                    }) {
                        BestMonthCard(formatMonth(bm), s.bestMonthCount)
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            // ── Progresión ──────────────────────────────────────────────────
            val p = state.progression
            if (p != null) {
                Text(stringResource(R.string.stats_screen_ascensos_por_mes_ult), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                MonthBars(p.monthlyCounts)
                Spacer(Modifier.height(Spacing.lg))

                if (p.maxGradePerQuarter.isNotEmpty()) {
                    Text(stringResource(R.string.stats_screen_grado_maximo_por_trimestre), style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        p.maxGradePerQuarter.takeLast(6).forEach { (q, g) ->
                            val c = gradeAccent(g)
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.10f))
                                .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(g, fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c, maxLines = 1)
                                Text(q.substringAfter('-') + " '" + q.substringBefore('-').takeLast(2), fontFamily = com.meteomontana.android.ui.theme.Mono,
                                    fontWeight = FontWeight.Bold, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.stats_screen_ultimas_12_semanas), style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.stats_weeks_out_chip, p.weeksOut.count { it }, p.weeksOut.size),
                        color = Color.White, fontFamily = com.meteomontana.android.ui.theme.Mono, fontWeight = FontWeight.Bold, fontSize = 9.5.sp,
                        modifier = Modifier.clip(CircleShape).background(Terra).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(Spacing.xs))
                Column(Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        p.weeksOut.forEachIndexed { i, out ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().height(26.dp)
                                    .background(weekColor(i, p.weeksOut, out), RoundedCornerShape(5.dp)))
                                Text(if (i == p.weeksOut.size - 1) stringResource(R.string.stats_now)
                                     else if (i % 3 == 0) "-${p.weeksOut.size - 1 - i}" else " ",
                                    fontSize = 8.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(Ok, stringResource(R.string.stats_legend_out))
                        LegendDot(Terra, stringResource(R.string.stats_legend_streak))
                        LegendDot(MaterialTheme.colorScheme.surfaceVariant, stringResource(R.string.stats_legend_not_out))
                    }
                }
                Spacer(Modifier.height(Spacing.lg))

                Text(stringResource(R.string.stats_screen_v2_tus_escuelas), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                val topSchools = p.perSchool.take(8)
                val maxSchool = (topSchools.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                topSchools.forEach { (school, count, maxGrade) ->
                    val isOpen = expandedSchool == school
                    // Tarjeta desplegable: nombre, barra proporcional, nº de ascensos y grado máximo en su color.
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { expandedSchool = if (isOpen) null else school },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$count", fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Terra)
                            Text(stringResource(R.string.stats_ascents_word), fontFamily = com.meteomontana.android.ui.theme.Mono, fontWeight = FontWeight.Bold,
                                fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(school, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurface)
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxWidth(count.toFloat() / maxSchool).height(6.dp).clip(CircleShape).background(Terra))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            maxGrade?.let { GradePill(it) }
                            Text(if (isOpen) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
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
                                    GradePill(e.grade ?: "—")
                                }
                            }
                            Text(stringResource(R.string.stats_screen_v2_ver_en_el_diario), style = EyebrowTextStyle.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { onOpenSchool(school) }
                                    .padding(vertical = 6.dp))
                        }
                    }
                    }
                }
            }

            if (state.summary == null && !state.loading) {
                InfoCard(stringResource(R.string.stats_screen_v3_marca_vias_como_hechas_y))
            }

        }
    }
}

