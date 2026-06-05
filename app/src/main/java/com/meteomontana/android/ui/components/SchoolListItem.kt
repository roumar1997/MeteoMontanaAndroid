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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.scoreColor

/**
 * Item de lista al estilo PWA "Cumbre":
 *
 *   01   ┌────┐  Becedas                                        ★
 *        │ 78 │  GRANITO · CASTILLA Y LEÓN
 *        │MUY │  ▰▰▰▰▰▰▰▰░░ heatmap horario              ● SECA
 *        │BUEN│
 *        └────┘
 *
 * El badge va con fondo `paper` claro y borde `rule`. El color del score
 * va en el heatmap horizontal, no en el badge: la PWA reserva el color
 * para el dato, no para el chrome.
 */
@Composable
fun SchoolListItem(
    rank: Int,
    school: School,
    todayScore: Int? = null,
    hourlyScores: List<Int>? = null,
    distanceKm: Double? = null,
    dry: Boolean? = null,
    onClick: () -> Unit,
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
        // Rank "01", "02"...
        Text(
            text = "%02d".format(rank),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )

        ScoreBadge(score = todayScore)

        // Nombre + eyebrow + heatmap
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = school.name,
                style = TextStyle(
                    fontFamily = Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.3).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = buildSubtitle(school, distanceKm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            HourlyHeatmapBar(scores = hourlyScores)
        }

        // ● SECA / ● MOJADA  (texto exacto de la PWA)
        Text(
            text = when (dry) {
                true  -> "● SECA"
                false -> "● MOJADA"
                null  -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (dry == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary
        )
    }
}

/**
 * Caja izquierda con el score de hoy.
 * Fondo `paper` claro + borde `rule`. Número grande serif. Label mono debajo.
 */
@Composable
private fun ScoreBadge(score: Int?) {
    Column(
        modifier = Modifier
            .size(width = 56.dp, height = 60.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = score?.toString() ?: "—",
            style = TextStyle(
                fontFamily = Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = scoreLabel(score),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
 * Tira horizontal de 10 celdas coloreadas según score por hora.
 * En la PWA mide unos 12px de alto y ocupa ancho completo de la columna.
 */
@Composable
private fun HourlyHeatmapBar(scores: List<Int>?) {
    val items: List<Int?> = scores?.take(10) ?: List(10) { null }
    Row(
        modifier = Modifier.fillMaxWidth().height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items.forEach { s ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = s?.let { scoreColor(it) }
                            ?: MaterialTheme.colorScheme.surfaceVariant
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
