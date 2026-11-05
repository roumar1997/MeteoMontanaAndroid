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

// Piezas de Estadísticas: colores, pastillas, tarjetas, barras y casillas por mes.

/** G: color de barra/etiqueta por GRADO (misma paleta que los topos). Los
 *  grados «blancos» (fáciles) no se ven sobre papel → tinta. */
@Composable
internal fun gradeAccent(grade: String?): Color {
    val gs = com.meteomontana.android.ui.theme.gradeChipStyle(grade)
    return if (gs.dark) MaterialTheme.colorScheme.onSurface else gs.stroke
}

/** De poco a mucho: azul → verde → naranja. Cada mes tiene su color según cuánto subiste (no todo el mismo tono). */
internal fun heatColor(t: Float): Color {
    val stops = listOf(Color(0xFF2973BF), Color(0xFF339466), Color(0xFFD97A1A))
    val x = t.coerceIn(0f, 1f) * 2f
    val i = x.toInt().coerceAtMost(1)
    return androidx.compose.ui.graphics.lerp(stops[i], stops[i + 1], x - i)
}

/** Verde si saliste esa semana; terracota las de la racha actual (las últimas seguidas); gris si no. */
@Composable
internal fun weekColor(i: Int, flags: List<Boolean>, out: Boolean): Color {
    if (!out) return MaterialTheme.colorScheme.surfaceVariant
    val streak = flags.reversed().takeWhile { it }.size
    return if (i >= flags.size - streak) Terra else Ok
}

/** Grado en una píldora con SU color (el de los topos). */
@Composable
internal fun GradePill(grade: String) {
    val c = gradeAccent(grade)
    Text(grade, color = c, fontFamily = com.meteomontana.android.ui.theme.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        modifier = Modifier.clip(CircleShape).background(c.copy(alpha = 0.14f))
            .border(1.dp, c.copy(alpha = 0.6f), CircleShape).padding(horizontal = 9.dp, vertical = 3.dp))
}

@Composable
internal fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(12.dp).height(12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** El mejor mes, destacado: calendario, nombre y número grande. */
@Composable
internal fun BestMonthCard(month: String, count: Int) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Terra.copy(alpha = 0.10f))
        .border(1.dp, Terra.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(52.dp).height(52.dp).clip(RoundedCornerShape(12.dp)).background(terraFillColor()),
            contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.EventAvailable, null,
                tint = Color.White, modifier = Modifier.width(28.dp).height(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.stats_best_month_title), style = EyebrowTextStyle, color = Terra)
            Text(month.replaceFirstChar { it.uppercase() }, fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold,
                fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Terra)
            Text(stringResource(R.string.stats_ascents_word), fontFamily = com.meteomontana.android.ui.theme.Mono, fontWeight = FontWeight.Bold,
                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal val MONTHS get() = CalendarLabels.monthsShort().map { it.uppercase() }

internal fun formatMonth(yyyyMm: String): String = runCatching {
    MONTHS[yyyyMm.substringAfter('-').toInt() - 1].lowercase() + " " + yyyyMm.take(4)
}.getOrDefault(yyyyMm)

@Composable
internal fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
internal fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, terra: Boolean = false) {
    Column(modifier
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, color = if (terra) Terra else MaterialTheme.colorScheme.onSurface)
        Text(label, style = EyebrowTextStyle.copy(fontSize = 8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun InfoCard(text: String) {
    Box(Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        .padding(Spacing.sm)) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Ascensos por mes (últimos 12): número sobre cada barra, el mejor mes en terracota sólido, el último (este mes) marcado
 * con un punto, una línea discontinua con la MEDIA y una frase con el total, la media y el mejor mes.
 */
@Composable
internal fun MonthBars(counts: List<Pair<String, Int>>) {
    val max = (counts.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val total = counts.sumOf { it.second }
    val avg = if (counts.isEmpty()) 0f else total / counts.size.toFloat()
    val best = counts.maxByOrNull { it.second }?.takeIf { it.second > 0 }
    val barLane = 96.dp
    val numberLane = 16.dp
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthTile("$total", stringResource(R.string.stats_in_12_months), false, Modifier.weight(1f))
            MonthTile("%.1f".format(avg), stringResource(R.string.stats_avg_month), false, Modifier.weight(1f))
            MonthTile(best?.let { MONTHS[it.first.substringAfter('-').toInt() - 1] } ?: "—",
                stringResource(R.string.stats_best_month_short) + (best?.let { " · ${it.second}" } ?: ""), true, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().drawBehind {
                // Línea discontinua de la media, a su altura dentro del carril de las barras.
                val y = numberLane.toPx() + barLane.toPx() * (1f - (avg / max).coerceIn(0f, 1f))
                drawLine(line.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            counts.forEachIndexed { i, (month, count) ->
                val isBest = best != null && best.first == month
                val isNow = i == counts.lastIndex
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(numberLane), contentAlignment = Alignment.BottomCenter) {
                        if (count > 0) Text(count.toString(), fontSize = 10.sp, fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                            color = if (isBest) Terra else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxWidth().height(barLane), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth()
                            .height((barLane.value * count / max).coerceAtLeast(if (count > 0) 4f else 1f).dp)
                            .background(if (count == 0) MaterialTheme.colorScheme.surfaceVariant else heatColor(count.toFloat() / max),
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                    }
                    Text(MONTHS[month.substringAfter('-').toInt() - 1].take(3), fontSize = 9.sp,
                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                        color = if (isNow) Terra else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Text(stringResource(R.string.stats_dashed_avg), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun MonthTile(value: String, label: String, terra: Boolean, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.background).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1,
            color = if (terra) Terra else MaterialTheme.colorScheme.onSurface)
        Text(label, fontFamily = com.meteomontana.android.ui.theme.Mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
