package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.ui.theme.scoreColor

private val DAY_NAMES = mapOf(
    "MON" to "LUN", "TUE" to "MAR", "WED" to "MIÉ", "THU" to "JUE",
    "FRI" to "VIE", "SAT" to "SÁB", "SUN" to "DOM"
)
private val DAY_FULL = mapOf(
    "MON" to "lunes", "TUE" to "martes", "WED" to "miércoles", "THU" to "jueves",
    "FRI" to "viernes", "SAT" to "sábado", "SUN" to "domingo"
)

@Composable
fun DayRow(day: DayForecast, dayIndex: Int, onClick: (() -> Unit)? = null) {
    val dt = java.time.LocalDate.parse(day.date)
    val dow = dt.dayOfWeek.name.take(3).uppercase() // MON, TUE, ...
    val label = DAY_NAMES[dow] ?: dow
    val full = when (dayIndex) {
        0 -> "hoy"
        1 -> "mañana"
        else -> DAY_FULL[dow] ?: ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(56.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                full,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = when {
                day.precipitationTotal > 0 -> "🌧"
                else                       -> "☁"
            },
            modifier = Modifier.width(28.dp)
        )
        // Barra score — más corta cuando llueve para dejar espacio al mm
        val hasRain = day.precipitationTotal > 0.0
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (day.avgScore / 100f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(scoreColor(day.avgScore))
            )
        }
        // Indicador mm de lluvia si llueve
        if (hasRain) {
            Text(
                text = "%.1fmm".format(day.precipitationTotal),
                modifier = Modifier.padding(start = 8.dp).width(52.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(
            text = "${day.tempMax.toInt()}°",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${day.tempMin.toInt()}°",
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${day.avgScore}",
            modifier = Modifier.padding(start = 12.dp).width(28.dp),
            style = MaterialTheme.typography.titleMedium,
            color = scoreColor(day.avgScore)
        )
    }
}
