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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.Forecast

/**
 * Bloque de scoring/forecast reutilizable.
 * Lo usa la pantalla de detalle de escuela y la pantalla Tab Tiempo.
 */
fun LazyListScope.forecastBody(
    forecast: Forecast,
    afterCurrentWeather: (LazyListScope.() -> Unit)? = null,
    onDayClick: ((Int) -> Unit)? = null
) {
    item {
        // El desglose "¿por qué este índice?" se abre solo desde el acordeón
        // de abajo (FactorsAccordion); el hero es solo presentación.
        var factorsExpanded by rememberSaveable { mutableStateOf(false) }
        Column {
            HeroSection(forecast)
            // Estado de la roca justo bajo el índice: es la pregunta nº1 de un
            // escalador (¿seca? ¿cuándo?). Antes vivía al final, en BestDayBar.
            RockStatusBand(forecast.current)
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                HourlyHeatmap(hours = forecast.hours)
            }
            FactorsAccordion(
                current = forecast.current,
                expanded = factorsExpanded,
                onToggle = { factorsExpanded = !factorsExpanded }
            )
        }
    }
    item { CurrentWeather(forecast.current) }
    afterCurrentWeather?.invoke(this)
    item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
    item { SectionTitle(stringResource(R.string.detail_next_hours)) }
    item {
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            HourlyScoreGrid(hours = forecast.hours)
        }
    }
    item { ConditionsGrid(forecast.current) }
    item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
    item { SectionTitle(stringResource(R.string.detail_next_days)) }
    itemsIndexed(forecast.days.take(7)) { i, d ->
        DayRow(day = d, dayIndex = i, onClick = onDayClick?.let { { it(i) } })
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
    // BestDayBar quitado: el score coloreado de cada fila ya lo dice (paridad iOS).
}

@Composable
fun HeroSection(forecast: Forecast) {
    val cur = forecast.current
    val verdict = if (cur.score >= 55) stringResource(R.string.detail_yes) else stringResource(R.string.detail_no)
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
            Text(stringResource(R.string.detail_index),
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

/**
 * Franja con el estado de la roca (seca/húmeda) y, si llovió, la estimación
 * de secado del backend. Verde si está lista, rojo si conviene esperar.
 * Fondo y borde tintados con el color de acento a baja opacidad para no
 * pelearse con el papel del tema.
 */
@Composable
fun RockStatusBand(cur: Current) {
    val dry = cur.dryRock
    val accent = if (dry) MaterialTheme.colorScheme.secondary
                 else MaterialTheme.colorScheme.error
    val subtitle = cur.drying?.message
        ?: if (dry) "Lista para escalar" else "Mejor esperar a que seque"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                if (dry) "● ROCA SECA" else "● ROCA HÚMEDA",
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FactorsAccordion(current: Current, expanded: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.detail_why_index),
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
fun CurrentWeather(cur: Current) {
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
fun ConditionsGrid(cur: Current) {
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
            ConditionCell("ROCA", if (cur.dryRock) stringResource(R.string.schools_rock_dry) else "HÚM", "", Modifier.weight(1f))
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
fun BestDayBar(forecast: Forecast) {
    val best = forecast.bestDay ?: return
    // El estado de la roca se muestra ahora arriba (RockStatusBand); aquí
    // solo queda el mejor día próximo.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(stringResource(R.string.detail_best_day),
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
