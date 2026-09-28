package com.meteomontana.android.ui.components

import com.meteomontana.android.util.CalendarLabels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.scoreColor
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R


/**
 * Fila de un día del forecast — RÉPLICA del layout iOS (que quedó mejor):
 * score grande coloreado a la izquierda, "Vie 3 Jul" y debajo
 * "MÁX 37° · MÍN 20° · 0 mm". Sin barra de progreso ni emoji (la barra
 * terra dominaba la pantalla y no aportaba sobre el número).
 */
@Composable
fun DayRow(day: DayForecast, dayIndex: Int, onClick: (() -> Unit)? = null) {
    val dt = java.time.LocalDate.parse(day.date)
    val title = "${CalendarLabels.daysShortMonFirst()[dt.dayOfWeek.value - 1]} ${dt.dayOfMonth} ${CalendarLabels.monthsShort()[dt.monthValue - 1]}"
    val mm = if (day.precipitationTotal < 0.05) "0 mm"
             else "%.1f mm".format(day.precipitationTotal)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${day.avgScore}",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = Serif, fontWeight = FontWeight.Bold),
            color = scoreColor(day.avgScore),
            modifier = Modifier.width(52.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.day_row_v2_max_1_s_min_2, day.tempMax.toInt(), day.tempMin.toInt(), mm),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
                color = if (day.precipitationTotal >= 1.0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
