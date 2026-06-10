package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.scoreColor
import com.meteomontana.android.ui.theme.scoreTextColor

/**
 * Item de lista al estilo PWA "Cumbre":
 *
 *   ┌──────┐ 01  Becedas                                       ★
 *   │  78  │     GRANITO · CASTILLA Y LEÓN · 1694 KM
 *   │ MUY  │     ▰▰▰▰▰▰▰▰▰▰ heatmap horario             ● SECA
 *   │ BUEN │
 *   └──────┘
 *
 * Badge tintado por score (fondo color suave + borde fuerte + número grande).
 * Heatmap 16dp con cuadrados saturados sin gap (como PWA).
 */
@Composable
fun SchoolListItem(
    rank: Int,
    school: School,
    todayScore: Int? = null,
    hourlyScores: List<Int>? = null,
    distanceKm: Double? = null,
    dry: Boolean? = null,
    rainMm: Double? = null,
    rainProb: Int? = null,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        ScoreBadge(score = todayScore)

        Column(modifier = Modifier.weight(1f)) {
            // Rank + nombre + estrella favorito
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "%02d".format(rank),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp)
                )
                Text(
                    text = school.name,
                    style = TextStyle(
                        fontFamily = Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .let { m -> if (onToggleFavorite != null) m.clickable(onClick = onToggleFavorite) else m }
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = buildSubtitle(school, distanceKm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HourlyHeatmapBar(
                    scores = hourlyScores,
                    modifier = Modifier.weight(1f)
                )
                DryWetTag(dry = dry, rainProb = rainProb, rainMm = rainMm)
            }
        }
    }
}

/**
 * Badge con fondo tintado por score (8% del color del score) + borde fuerte.
 * Número grande serif. Label mono pequeñita debajo.
 */
@Composable
private fun ScoreBadge(score: Int?) {
    val color = score?.let { scoreColor(it) } ?: MaterialTheme.colorScheme.outline
    val bg = if (score != null) color.copy(alpha = 0.12f)
             else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .size(width = 64.dp, height = 72.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(1.5.dp, color, RoundedCornerShape(2.dp))
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = score?.toString() ?: "—",
            style = TextStyle(
                fontFamily = Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp,
            ),
            color = if (score != null) color else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = scoreLabel(score),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            ),
            color = if (score != null) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DryWetTag(dry: Boolean?, rainProb: Int?, rainMm: Double?) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = when (dry) {
                true  -> "● SECA"
                false -> "● MOJADA"
                null  -> ""
            },
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            ),
            color = if (dry == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary
        )
        if (dry == false && (rainProb ?: 0) > 0) {
            Text(
                text = "${rainProb}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (dry == false && (rainMm ?: 0.0) > 0.0) {
            Text(
                text = "%.1f mm".format(rainMm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun scoreLabel(score: Int?): String = when {
    score == null -> ""
    score >= 85   -> "EXCELENTE"
    score >= 70   -> "MUY BUENO"
    score >= 55   -> "BUENO"
    score >= 40   -> "REGULAR"
    else          -> "MALO"
}

/**
 * Tira horizontal de 10 celdas saturadas. Sin gap entre celdas (como la PWA).
 * Altura 16dp para que el color sea bien visible.
 */
@Composable
private fun HourlyHeatmapBar(scores: List<Int>?, modifier: Modifier = Modifier) {
    val items: List<Int?> = scores?.takeIf { it.isNotEmpty() }?.take(10) ?: List(10) { null }
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items.forEach { s ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = s?.let { scoreColor(it) }
                            ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
            )
        }
    }
}

private fun buildSubtitle(school: School, distanceKm: Double?): String {
    val parts = buildList {
        school.rockType?.let { add(it.uppercase()) }
        school.region?.let   { add(it) }
        distanceKm?.let      { add("${it.toInt()} KM") }
    }
    return parts.joinToString("  ·  ")
}
