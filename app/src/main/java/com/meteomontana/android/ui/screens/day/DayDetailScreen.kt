package com.meteomontana.android.ui.screens.day

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.domain.model.HourForecast
import com.meteomontana.android.ui.components.WmoWeatherIcon
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.scoreColor

@Composable
fun DayDetailScreen(
    onBack: () -> Unit,
    viewModel: DayDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val s = state) {
            DayDetailUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is DayDetailUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is DayDetailUiState.Loaded -> LoadedBody(s, onBack)
        }
    }
}

@Composable
private fun LoadedBody(s: DayDetailUiState.Loaded, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header(title = s.title, onBack = onBack) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
        item { Spacer(Modifier.height(Spacing.lg)) }
        item { DayScoreHero(day = s.day) }
        item { Spacer(Modifier.height(Spacing.md)) }
        item { ConditionsTable(day = s.day, hoursOfDay = s.hoursOfDay) }
        item { Spacer(Modifier.height(Spacing.xl)) }
        item { SectionTitle("PRÓXIMAS 24H") }
        if (s.hoursOfDay.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    Text("Sin datos horarios para este día.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(s.hoursOfDay) { h -> HourRow(h) }
        }
        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "‹ Volver",
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            title,
            style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.size(64.dp))
    }
}

@Composable
private fun DayScoreHero(day: DayForecast) {
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        Text("ÍNDICE DEL DÍA",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            ScoreBar(score = day.avgScore, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(Spacing.md))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    day.avgScore.toString(),
                    style = TextStyle(
                        fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = scoreColor(day.avgScore)
                )
                Text("/100",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "● ${day.scoreLabel.uppercase()} PARA ESCALAR",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp
            ),
            color = scoreColor(day.avgScore)
        )
    }
}

@Composable
private fun ScoreBar(score: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(20) { i ->
            val cellScore = (i + 1) * 5
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().height(20.dp).background(
                    if (cellScore <= score) scoreColor(cellScore)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun ConditionsTable(day: DayForecast, hoursOfDay: List<HourForecast>) {
    val windMax = hoursOfDay.maxOfOrNull { it.windSpeed } ?: 0.0
    val rainProbMax = hoursOfDay.maxOfOrNull { it.precipitationProbability } ?: 0
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Cell("MÁX", "${day.tempMax.toInt()}°", Modifier.weight(1f))
            Cell("MÍN", "${day.tempMin.toInt()}°", Modifier.weight(1f))
            Cell("VIENTO", "${windMax.toInt()}", Modifier.weight(1f))
            Cell("UV", "—", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Cell("LLUVIA", "%.1f mm".format(day.precipitationTotal), Modifier.weight(1f))
            Cell("PROB.", "${rainProbMax}%", Modifier.weight(1f))
            Cell("AMANECER", "—", Modifier.weight(1f))
            Cell("LUZ", "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(Spacing.md)) {
        Text(label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = Mono, letterSpacing = 0.6.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,
            style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Text(text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HourRow(h: HourForecast) {
    val hourLabel = h.time.substringAfter('T').take(5)
    Column(Modifier.fillMaxWidth()
        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.size(48.dp, 32.dp)) {
                Text(hourLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onBackground)
                Text(h.score.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = Mono, fontWeight = FontWeight.Bold
                    ),
                    color = scoreColor(h.score))
            }
            Spacer(Modifier.size(Spacing.md))
            WmoWeatherIcon(code = h.weatherCode, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(Spacing.md))
            Text("${h.temperature.toInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(40.dp, 20.dp))
            Text("${h.precipitationProbability}%",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = if (h.precipitationProbability > 30)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp, 20.dp))
            Text(
                text = if (h.precipitation > 0.0) "%.1fmm".format(h.precipitation) else "–",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = if (h.precipitation > 0.0)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp, 20.dp)
            )
            Spacer(Modifier.weight(1f))
            Text("${h.windSpeed.toInt()} km/h",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (h.precipitation > 0.0 || h.precipitationProbability > 0) {
            Spacer(Modifier.height(4.dp))
            RainBar(mm = h.precipitation, prob = h.precipitationProbability)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
}

@Composable
private fun RainBar(mm: Double, prob: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Probabilidad: barra azul que ocupa prob% del ancho
        Box(modifier = Modifier
            .weight(prob.coerceIn(0, 100).toFloat() / 100f + 0.001f)
            .height(4.dp)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.55f)))
        // Lluvia mm: barra más oscura (max 10mm = full)
        val mmRatio = (mm / 10.0).coerceIn(0.0, 1.0).toFloat()
        if (mmRatio > 0f) {
            Box(modifier = Modifier
                .weight(mmRatio + 0.001f)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.error))
        }
        Spacer(Modifier.weight(1f - (prob / 100f).coerceAtMost(1f)))
    }
}

