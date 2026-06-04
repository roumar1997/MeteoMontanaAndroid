package com.meteomontana.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.data.api.dto.CurrentDto
import com.meteomontana.android.data.api.dto.ForecastDto

/**
 * Bloque de scoring/forecast reutilizable.
 * Lo usa la pantalla de detalle de escuela y la pantalla Tab Tiempo.
 */
fun LazyListScope.forecastBody(forecast: ForecastDto) {
    item { HeroSection(forecast) }
    item {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            HourlyHeatmap(hours = forecast.hours)
        }
    }
    item { FactorsAccordion(forecast.current) }
    item { CurrentWeather(forecast.current) }
    item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
    item { SectionTitle("PRÓXIMAS 16 HORAS") }
    item {
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            HourlyScoreGrid(hours = forecast.hours)
        }
    }
    item { ConditionsGrid(forecast.current) }
    item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
    item { SectionTitle("PRÓXIMOS 7 DÍAS") }
    itemsIndexed(forecast.days.take(7)) { i, d ->
        DayRow(day = d, dayIndex = i)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
    item { BestDayBar(forecast) }
}

@Composable
fun HeroSection(forecast: ForecastDto) {
    val cur = forecast.current
    val verdict = if (cur.score >= 55) "SÍ" else "NO"
    val window = forecast.bestWindow

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            verdict,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            color = if (cur.score >= 55) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.padding(start = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("¿PUEDO ESCALAR HOY?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (window != null) {
                Text("Óptimo entre ${window.start}–${window.end}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("ÍNDICE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(cur.score.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("/100",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
fun FactorsAccordion(current: CurrentDto) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("¿POR QUÉ ESTE ÍNDICE?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(expanded) { FactorList(current.factors) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
}

@Composable
fun CurrentWeather(cur: CurrentDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${cur.temperature.toInt()}°",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(cloudCoverLabel(cur.cloudCover),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text("VIENTO ${cur.windSpeed.toInt()} km/h  ·  HUM ${cur.humidity.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ConditionsGrid(cur: CurrentDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SectionTitle("CONDICIONES AHORA")
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConditionCell("HUMEDAD", "${cur.humidity.toInt()}", "%", Modifier.weight(1f))
            ConditionCell("VIENTO", "${cur.windSpeed.toInt()}", "km/h", Modifier.weight(1f))
            ConditionCell("LLUVIA 24H", "${cur.precip24h}", "mm", Modifier.weight(1f))
            ConditionCell("NUBES", "${cur.cloudCover}", "%", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConditionCell("LLUVIA 72H", "${cur.precip72h}", "mm", Modifier.weight(1f))
            ConditionCell("ROCÍO", cur.dewPoint?.let { "${it.toInt()}" } ?: "—", "°", Modifier.weight(1f))
            ConditionCell("PROB LLUVIA", "${cur.precipitationProbability}", "%", Modifier.weight(1f))
            ConditionCell("ROCA", if (cur.dryRock) "SECA" else "HÚM", "", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConditionCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(unit, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        }
    }
}

@Composable
fun BestDayBar(forecast: ForecastDto) {
    val best = forecast.bestDay ?: return
    val cur = forecast.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (cur.dryRock) "● ROCA SECA" else "● ROCA HÚMEDA",
            style = MaterialTheme.typography.labelMedium,
            color = if (cur.dryRock) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.padding(start = 16.dp))
        Column {
            Text("★ MEJOR DÍA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("En ${best.daysFromToday}d (${best.score})",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun cloudCoverLabel(cover: Int): String = when {
    cover < 20 -> "Despejado"
    cover < 50 -> "Parcialmente nublado"
    cover < 80 -> "Mayormente nublado"
    else       -> "Cubierto"
}
