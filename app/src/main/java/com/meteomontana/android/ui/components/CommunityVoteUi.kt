package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.model.OrientationSummary
import com.meteomontana.android.domain.model.SunHours
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra

/**
 * UI de la votación comunitaria (C2/C5). Regla de diseño (DESIGN.md): todo lo
 * VOTABLE lleva el mismo lenguaje — chip con borde DISCONTINUO terra + ▾.
 * Se aprende una vez y se reconoce en toda la app.
 */

val ASPECTS = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

/** Chip pulsable con borde discontinuo terra + ▾ (orientación, grado, año…). */
@Composable
fun VotableChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
    Row(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = Terra, style = Stroke(width = 3f, pathEffect = dash),
                    cornerRadius = CornerRadius(50f, 50f)
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text, style = EyebrowTextStyle.copy(fontSize = 11.sp), color = Terra)
        Text("▾", fontSize = 10.sp, color = Terra)
    }
}

/** Barras de votos (compartidas por orientación y grado). */
@Composable
private fun VoteBars(votes: Map<String, Int>, highlight: String?, myVote: String?) {
    val max = (votes.values.maxOrNull() ?: 1).coerceAtLeast(1)
    votes.entries.sortedByDescending { it.value }.forEach { (option, count) ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                option,
                style = EyebrowTextStyle.copy(fontSize = 12.sp),
                color = if (option == highlight) Terra else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(38.dp)
            )
            Box(Modifier.weight(1f).height(14.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))) {
                Box(Modifier.fillMaxWidth(count / max.toFloat()).height(14.dp)
                    .background(if (option == highlight) Terra else Terra.copy(alpha = 0.45f),
                        RoundedCornerShape(7.dp)))
            }
            Text(
                "$count" + if (option == myVote) " · tú ✓" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Contenido del diálogo de votar ORIENTACIÓN. */
@Composable
fun OrientationVoteContent(
    summary: OrientationSummary?,
    onVote: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text("¿HACIA DÓNDE MIRA ESTA PARED?", style = EyebrowTextStyle, color = Terra)
        Text(
            "Vota la comunidad; se muestra la más votada. Un voto por persona — puedes cambiarlo.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        if (summary != null && summary.votes.isNotEmpty()) {
            VoteBars(summary.votes, summary.consensus, summary.myVote)
        } else {
            Text("Sin votos todavía. ¡Sé el primero!",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ASPECTS.take(4).forEach { a -> AspectChip(a, summary?.myVote == a) { onVote(a) } }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ASPECTS.drop(4).forEach { a -> AspectChip(a, summary?.myVote == a) { onVote(a) } }
        }
    }
}

@Composable
private fun AspectChip(aspect: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(
                if (selected) Terra else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(6.dp)
            )
            .border(1.dp, if (selected) Terra else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            aspect + if (selected) " ✓" else "",
            style = EyebrowTextStyle.copy(fontSize = 11.sp),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Contenido del diálogo de votar GRADO (C5). */
@Composable
fun GradeVoteContent(
    summary: GradeSummary?,
    canVote: Boolean,
    onVote: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text("¿QUÉ GRADO LE DAS?", style = EyebrowTextStyle, color = Terra)
        Text(
            if (canVote)
                "El grado que se muestra es el consenso (con 3+ votos). El del equipador queda como referencia."
            else
                "Solo puede votar quien la tiene en su diario (próbala o encadénala primero).",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        val s = summary ?: return@Column
        if (s.votes.isNotEmpty()) VoteBars(s.votes, s.displayedGrade, s.myVote)
        Row(
            Modifier.fillMaxWidth()
                .padding(top = 8.dp)
                .background(Terra.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Mostrado", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                (s.displayedGrade ?: "—") +
                    (s.setterGrade?.takeIf { it != s.displayedGrade }
                        ?.let { "  ·  equipador: $it" } ?: ""),
                style = EyebrowTextStyle.copy(fontSize = 12.sp), color = Terra
            )
        }
        if (canVote) {
            Spacer(Modifier.height(10.dp))
            GradePicker(current = s.myVote ?: s.displayedGrade, onPick = onVote)
        }
    }
}

/** Selector de grado francés: rejilla completa (número + letra + plus). */
@Composable
private fun GradePicker(current: String?, onPick: (String) -> Unit) {
    val suffixes = listOf("a", "a+", "b", "b+", "c", "c+")
    Column {
        Text("TU VOTO", style = EyebrowTextStyle.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        listOf("4", "5", "6", "7", "8").forEach { n ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 2.dp)) {
                suffixes.forEach { suf ->
                    val g = n + suf
                    AspectChip(g, selected = current == g) { onPick(g) }
                }
            }
        }
    }
}

/** Tira horaria de sol (amarillo = al sol, azul tinta = sombra). */
@Composable
fun SunStrip(sun: SunHours, modifier: Modifier = Modifier) {
    if (sun.hours.isEmpty()) return
    val sunColor = Color(0xFFE8B84B)
    val shadeColor = Color(0xFF3D4A5C)
    Column(modifier) {
        // Titulo + leyenda EN LA MISMA FILA (feedback N1: aprovechar el espacio).
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("SOL EN ESTA PARED · HOY", style = EyebrowTextStyle.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendDot(Color(0xFFE8B84B), "Sol"); LegendDot(Color(0xFF3D4A5C), "Sombra")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            sun.hours.forEach { h ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(18.dp)
                        .background(if (h.inSun) sunColor else shadeColor, RoundedCornerShape(4.dp)))
                    val hour = h.time.substringAfter('T').take(2).trimStart('0').ifEmpty { "0" }
                    Text(hour, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(9.dp).height(9.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
