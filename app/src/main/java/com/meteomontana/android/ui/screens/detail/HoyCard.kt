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
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .guideTarget("detail.hoy")
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (good) stringResource(R.string.detail_yes) else stringResource(R.string.detail_no),
                color = verdictColor, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 38.sp
            )
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(cur.score.toString(), color = MaterialTheme.colorScheme.primary, fontFamily = Serif,
                    fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Text("/100", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "● " + stringResource(if (cur.dryRock) R.string.detail_rock_dry_caps else R.string.detail_rock_wet_caps),
                    color = rockColor, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp
                )
                forecast.bestWindow?.let {
                    Text(stringResource(R.string.detail_best_window, it.start, it.end),
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                }
            }
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        AnimatedVisibility(open) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                val subtitle = cur.drying?.message
                    ?: if (cur.dryRock) stringResource(R.string.forecast_body_v3_lista_para_escalar)
                    else stringResource(R.string.forecast_body_v3_mejor_esperar_a_que_seque)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                // Al abrir la tarjeta sale TODO a la vez (porqué del índice + mejor hora por sector).
                FactorsAccordion(current = cur, expanded = factorsOpen, onToggle = { factorsOpen = !factorsOpen }, alwaysOpen = true)
                // Mejor hora por sector: se pide ahora, al abrir la tarjeta (no al entrar en la escuela).
                SectorHoursSection(forecast.schoolId, alwaysOpen = true)
                Box(Modifier.padding(vertical = 8.dp)) { HourlyScoreGrid(hours = forecast.hours) }
            }
        }
    }
}
