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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.theme.scoreColor
import com.meteomontana.android.ui.theme.scoreTextColor

/**
 * Item de lista al estilo PWA "Cumbre":
 *  - Caja de score a la izquierda (89 EXCELENTE)
 *  - Nombre + ROCA · REGION · KM
 *  - Barra heatmap horario (10 cuadritos) — placeholder hasta endpoint /scores
 *  - Indicador "SECA" / "HÚMEDA" a la derecha
 *
 * Si todayScore o hourlyScores son null mostramos placeholders (—).
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Rank
        Text(
            text = "%02d".format(rank),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )

        // Caja de score
        ScoreBadge(score = todayScore)

        // Contenido
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = school.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildSubtitle(school, distanceKm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            HourlyHeatmapBar(scores = hourlyScores)
        }

        // SECA / HÚMEDA
        Text(
            text = when (dry) {
                true  -> "● SECA"
                false -> "● HÚMEDA"
                null  -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (dry == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun ScoreBadge(score: Int?) {
    val bg = score?.let { scoreColor(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    val fg = score?.let { scoreTextColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .size(width = 60.dp, height = 56.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = score?.toString() ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            color = fg
        )
        Text(
            text = scoreLabel(score),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp),
            color = fg
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

@Composable
private fun HourlyHeatmapBar(scores: List<Int>?) {
    // 10 cuadraditos. Si no hay datos mostramos placeholders grises.
    val items: List<Int?> = scores?.take(10) ?: List(10) { null }
    Row(
        modifier = Modifier.fillMaxWidth().height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
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
