package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meteomontana.android.data.api.dto.HourForecastDto
import com.meteomontana.android.ui.theme.scoreColor
import com.meteomontana.android.ui.theme.scoreTextColor

/**
 * Grid horizontal "PRÓXIMAS N HORAS" — para cada hora:
 *  hora · icono · score · temp · viento.
 * Scrollable horizontalmente.
 */
@Composable
fun HourlyScoreGrid(
    hours: List<HourForecastDto>,
    modifier: Modifier = Modifier,
    hoursAhead: Int = 16
) {
    val take = hours.take(hoursAhead)
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(take) { h ->
            HourCell(h)
        }
    }
}

@Composable
private fun HourCell(h: HourForecastDto) {
    Column(
        modifier = Modifier.width(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = h.time.substring(11, 13),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = weatherIcon(h),
            style = MaterialTheme.typography.bodyLarge
        )
        // Score box
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(width = 56.dp, height = 36.dp)
                .background(scoreColor(h.score)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = h.score.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = scoreTextColor(h.score)
            )
        }
        Text(
            text = "${h.temperature.toInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (h.precipitation > 0) "${h.precipitation}mm" else "—",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${h.windSpeed.toInt()}km/h",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun weatherIcon(h: HourForecastDto): String = when {
    h.precipitation > 0.5  -> "🌧"
    h.cloudCover > 70      -> "☁"
    h.cloudCover > 30      -> "⛅"
    else                   -> "☀"
}
