package com.meteomontana.android.ui.components

import com.meteomontana.android.util.CalendarLabels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.scoreColor
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R


private val DayCardShape = RoundedCornerShape(14.dp)

/**
 * Un día del forecast como TARJETA, con la nota dentro de un anillo del color de su nota, igual que la lista de
 * Escuelas (Álvaro, 2026-10-22; espejo de DayRow en SchoolForecastViews.swift). Debajo del día:
 * "MÁX 37° · MÍN 20° · 0 mm". Flecha si al tocarla se abre el detalle.
 */
@Composable
fun DayRow(day: DayForecast, dayIndex: Int, onClick: (() -> Unit)? = null) {
    val dt = java.time.LocalDate.parse(day.date)
    val title = "${CalendarLabels.daysShortMonFirst()[dt.dayOfWeek.value - 1]} ${dt.dayOfMonth} ${CalendarLabels.monthsShort()[dt.monthValue - 1]}"
    val mm = if (day.precipitationTotal < 0.05) "0 mm"
             else "%.1f mm".format(day.precipitationTotal)

    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(DayCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, DayCardShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(42.dp).border(3.dp, scoreColor(day.avgScore), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("${day.avgScore}", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.day_row_v2_max_1_s_min_2, day.tempMax.toInt(), day.tempMin.toInt(), mm),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
                color = if (day.precipitationTotal >= 1.0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
