package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.HourForecast
import com.meteomontana.android.domain.util.fromNow
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Rain
import com.meteomontana.android.ui.theme.scoreColor
import java.util.Locale

/**
 * «PRÓXIMAS 16 H»: por hora, hora · icono · puntuación · temperatura · lluvia (si la hay) · viento.
 * Mismas medidas que `HoursGrid` de iOS; [compact] es la versión apretada de la ficha (caben ~10 horas).
 */
@Composable
fun HourlyScoreGrid(
    hours: List<HourForecast>,
    modifier: Modifier = Modifier,
    hoursAhead: Int = 16,
    compact: Boolean = false
) {
    val take = hours.fromNow(hoursAhead)
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
    ) {
        items(take) { h -> HourCell(h, compact) }
    }
}

@Composable
private fun HourCell(h: HourForecast, compact: Boolean) {
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)
    ) {
        MonoText(h.time.substring(11, 13), if (compact) 10.sp else 11.sp, ink2)
        WmoWeatherIcon(code = h.weatherCode, size = if (compact) 16.dp else 22.dp, tint = ink2)
        Box(
            Modifier.size(width = if (compact) 28.dp else 30.dp, height = if (compact) 20.dp else 30.dp)
                .clip(RoundedCornerShape(6.dp)).background(scoreColor(h.score)),
            contentAlignment = Alignment.Center
        ) { MonoText(h.score.toString(), 11.sp, Color.White, bold = true) }
        MonoText("${h.temperature.toInt()}°", 11.sp, ink2)
        if (h.precipitation > 0) {
            MonoText(String.format(Locale.ROOT, "%.1f mm", h.precipitation), 9.sp, Rain, bold = true)
        } else if (!compact) {
            MonoText(" ", 9.sp, ink2)
        }
        MonoText("${h.windSpeed.toInt()} km/h", 9.sp, ink2)
    }
}

@Composable
private fun MonoText(text: String, size: TextUnit, color: Color, bold: Boolean = false) {
    Text(text, fontFamily = Mono, fontSize = size, color = color, maxLines = 1,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}
