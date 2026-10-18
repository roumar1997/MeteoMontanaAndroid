package com.meteomontana.android.ui.screens.detail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.ui.components.FactorsAccordion
import com.meteomontana.android.ui.components.HourlyScoreGrid
import com.meteomontana.android.ui.guide.guideTarget
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif

/**
 * Tarjeta HOY de la ficha, compacta: SÍ/NO + nota + estado de la roca + ventana óptima. Se despliega para ver las
 * próximas 16 horas y el porqué del índice (2026-10-14; antes ocupaba media pantalla con la ficha entera del tiempo,
 * que ahora vive en la pestaña TIEMPO).
 */
@Composable
fun HoyCard(forecast: Forecast, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    var factorsOpen by rememberSaveable { mutableStateOf(false) }
    val cur = forecast.current
    val good = cur.score >= 55
    val verdictColor = if (good) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val rockColor = if (cur.dryRock) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val shape = RoundedCornerShape(10.dp)

    Column {
    Column(
        modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .guideTarget("detail.hoy")
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (good) stringResource(R.string.detail_yes) else stringResource(R.string.detail_no),
                color = verdictColor, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp
            )
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(cur.score.toString(), color = com.meteomontana.android.ui.theme.scoreColor(cur.score), fontFamily = Serif,
                    fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text("/100", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontSize = 9.sp,
                    modifier = Modifier.padding(start = 1.dp, bottom = 3.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "● " + stringResource(if (cur.dryRock) R.string.detail_rock_dry_caps else R.string.detail_rock_wet_caps),
                    color = rockColor, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.5.sp
                )
                forecast.bestWindow?.let {
                    Text(stringResource(R.string.detail_best_window, it.start, it.end),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp, maxLines = 1)
                }
            }
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        // Las próximas 16 horas van SIEMPRE a la vista (Álvaro, 2026-10-18).
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Box(Modifier.padding(vertical = 6.dp)) { HourlyScoreGrid(hours = forecast.hours) }
        AnimatedVisibility(open) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                val subtitle = cur.drying?.message
                    ?: if (cur.dryRock) stringResource(R.string.forecast_body_v3_lista_para_escalar)
                    else stringResource(R.string.forecast_body_v3_mejor_esperar_a_que_seque)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                FactorsAccordion(current = cur, expanded = factorsOpen, onToggle = { factorsOpen = !factorsOpen }, alwaysOpen = true)
            }
        }
    }
    // Mejor hora por sector: tarjeta propia y llamativa, debajo (no dentro del «por qué este índice»).
    SectorHoursSection(forecast.schoolId, dryRock = cur.dryRock, dryingMessage = cur.drying?.message,
        rainHours = todayRainHours(forecast.hours))
    }
}

/** Horas (0-23) de HOY con lluvia en la previsión; «hoy» = el día de la primera hora que trae. */
private fun todayRainHours(hours: List<com.meteomontana.android.domain.model.HourForecast>): Set<Int> {
    val day = hours.firstOrNull()?.time?.take(10) ?: return emptySet()
    return hours.filter { it.time.startsWith(day) && it.precipitation >= 0.1 }
        .mapNotNull { it.time.drop(11).take(2).toIntOrNull() }.toSet()
}
