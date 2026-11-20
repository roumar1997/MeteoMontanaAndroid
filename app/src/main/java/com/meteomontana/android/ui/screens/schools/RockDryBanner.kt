package com.meteomontana.android.ui.screens.schools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.meteomontana.android.R
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.domain.usecase.forecast.RockDayState
import com.meteomontana.android.domain.usecase.forecast.RockDryOutlook
import com.meteomontana.android.ui.theme.Ok
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.datetime.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

private val WetBlue = Color(0xFF2563C7)
private val DubiousAmber = Color(0xFFD9962B)

/** Carga (y recuerda) la previsión de una escuela para pintar «seca en ~X h» en la tarjeta del mapa. */
@HiltViewModel
class RockDryOutlookViewModel @Inject constructor(private val getForecast: GetForecastUseCase) : ViewModel() {
    private val cache = HashMap<String, RockDryOutlook?>()

    suspend fun outlook(schoolId: String): RockDryOutlook? {
        if (cache.containsKey(schoolId)) return cache[schoolId]
        val result = runCatching {
            val f = getForecast(schoolId)
            val today = java.time.LocalDate.now()
            RockDryOutlook.of(f.current, f.days, LocalDate(today.year, today.monthValue, today.dayOfMonth))
        }.getOrNull()
        if (result != null) cache[schoolId] = result
        return result
    }
}

internal fun dayName(d: LocalDate, full: Boolean = true): String =
    java.time.DayOfWeek.of(d.dayOfWeek.ordinal + 1)
        .getDisplayName(if (full) TextStyle.FULL else TextStyle.SHORT, Locale.getDefault()).lowercase()

private fun stateColor(s: RockDayState) = when (s) {
    RockDayState.WET -> WetBlue
    RockDayState.DUBIOUS -> DubiousAmber
    RockDayState.DRY -> Ok
}

/** Texto principal y secundario del estado de la roca. */
@Composable
internal fun rockDryTexts(o: RockDryOutlook): Pair<String, String?> = when {
    o.wet && o.dryInHours != null -> stringResource(R.string.dry_wet_hours, o.dryInHours!!) to null
    o.wet && o.dryFromDate != null -> stringResource(R.string.dry_wet_day, dayName(o.dryFromDate!!)) to null
    o.wet -> stringResource(R.string.dry_wet) to null
    o.worsensOnDate != null -> {
        val head = o.dryUntilDate?.let { stringResource(R.string.dry_until, dayName(it)) } ?: stringResource(R.string.dry_now)
        head to stringResource(R.string.dry_worsens, dayName(o.worsensOnDate!!))
    }
    else -> stringResource(R.string.dry_now) to stringResource(R.string.dry_all_week)
}

/** Banda de estado (azul mojada / verde seca) con el detalle y debajo la franja de 5 días, todo junto. */
@Composable
internal fun RockDryBanner(o: RockDryOutlook, modifier: Modifier = Modifier) {
    val (title, sub) = rockDryTexts(o)
    val tint = if (o.wet) WetBlue else Ok
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tint)
            sub?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (o.strip.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            o.strip.forEachIndexed { i, (date, state) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(stateColor(state)))
                    Text(
                        (if (i == 0) stringResource(R.string.dry_today) else dayName(date, full = false)).uppercase(), fontSize = 9.sp, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
