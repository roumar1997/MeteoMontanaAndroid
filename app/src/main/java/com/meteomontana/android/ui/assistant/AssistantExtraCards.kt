package com.meteomontana.android.ui.assistant

import com.meteomontana.android.presentation.assistant.AssistantLabels
import com.meteomontana.android.presentation.assistant.AssistantPresenter
import com.meteomontana.android.presentation.assistant.*
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.AssistantRestriction
import com.meteomontana.android.domain.model.AssistantRestrictions
import com.meteomontana.android.ui.theme.Bad
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Rain
import com.meteomontana.android.ui.theme.Warn
import com.meteomontana.android.ui.theme.scoreColor

/*
 * Tarjetas de las respuestas nuevas del asistente (octubre de 2026): restricciones y plan de viaje. Mismo estilo que las
 * de sectores: papel, esquinas de 14 y borde fino. Espejo de `AssistantExtraCards.swift` (iOS).
 */

private val CardShape = RoundedCornerShape(14.dp)

@Composable
private fun Modifier.assistantCard() = this.clip(CardShape).background(MaterialTheme.colorScheme.surface)
    .border(1.dp, MaterialTheme.colorScheme.outline, CardShape)

/** Las restricciones de la zona o de una escuela; tocar una abre su escuela. */
@Composable
fun AssistantRestrictionsView(r: AssistantRestrictions, presenter: AssistantPresenter, onOpenSchool: (String) -> Unit) {
    if (r.items.isEmpty()) return
    Column(Modifier.fillMaxWidth().assistantCard()) {
        r.items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            RestrictionRow(item, presenter) { onOpenSchool(item.schoolId) }
        }
        Text(presenter.restrictionsDisclaimer(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

@Composable
private fun RestrictionRow(item: AssistantRestriction, presenter: AssistantPresenter, onClick: () -> Unit) {
    val color = when (item.kind) {
        "CLOSED" -> Bad
        "REGULATED" -> Warn
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(presenter.restrictionKind(item.kind).uppercase(), color = if (item.active) Color.White else color,
            fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp,
            modifier = Modifier.clip(CircleShape).background(if (item.active) color else color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${item.schoolName} · ${presenter.restrictionPlace(item)}", color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(presenter.restrictionWhen(item), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono,
                fontWeight = FontWeight.Bold, fontSize = 11.sp)
            item.reason?.takeIf { it.isNotEmpty() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            item.sourceName?.takeIf { it.isNotEmpty() }?.let {
                Text(presenter.restrictionsSource(it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp))
    }
}

/** El plan de un viaje: un día por fila con su mejor escuela y su nota; tocar abre la escuela. */
@Composable
fun AssistantTripView(plan: List<AssistantPresenter.TripDay>, presenter: AssistantPresenter, onOpenSchool: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().assistantCard()) {
        plan.forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenSchool(day.schoolId) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(presenter.dayLabel(day.date), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono,
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(74.dp))
                Text(day.schoolName, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
                if (day.rainy) Icon(Icons.Outlined.WaterDrop, null, tint = Rain, modifier = Modifier.size(14.dp))
                Box(
                    Modifier.size(width = 34.dp, height = 26.dp).clip(RoundedCornerShape(8.dp)).background(scoreColor(day.score)),
                    contentAlignment = Alignment.Center
                ) { Text("${day.score}", color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
    }
}
