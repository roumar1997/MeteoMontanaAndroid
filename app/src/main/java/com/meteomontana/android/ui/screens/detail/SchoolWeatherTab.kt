package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.ui.components.ConditionsGrid
import com.meteomontana.android.ui.components.CurrentWeather
import com.meteomontana.android.ui.components.DayRow
import com.meteomontana.android.ui.components.HourlyHeatmap
import com.meteomontana.android.ui.components.SectionTitle
import com.meteomontana.android.ui.guide.guideTarget

/**
 * Pestaña TIEMPO de la ficha: lo que ya no cabe en la tarjeta HOY — franja de horas, el tiempo de ahora, las
 * condiciones y los próximos 7 días. (El SÍ/NO, la nota y las 16 horas viven en [HoyCard].)
 *
 * [bulletin] = el boletín de montaña AEMET (solo escuelas de un macizo español), si lo hay.
 */
@Composable
fun SchoolWeatherTab(
    forecast: Forecast,
    bulletin: (@Composable () -> Unit)? = null,
    onDayClick: ((Int) -> Unit)? = null
) {
    Column {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).guideTarget("detail_weather.heat")) {
            HourlyHeatmap(hours = forecast.hours)
        }
        Box(Modifier.guideTarget("detail_weather.now")) { CurrentWeather(forecast.current) }
        bulletin?.invoke()
        ConditionsGrid(forecast.current)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(Modifier.guideTarget("detail_weather.days")) {
            SectionTitle(stringResource(R.string.detail_next_days))
            forecast.days.take(7).forEachIndexed { i, d ->
                DayRow(day = d, dayIndex = i, onClick = onDayClick?.let { { it(i) } })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
        }
    }
}
