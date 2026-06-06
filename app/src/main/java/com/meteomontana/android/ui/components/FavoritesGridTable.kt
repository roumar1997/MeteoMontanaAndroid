package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.meteomontana.android.domain.model.FavoritesGrid
import com.meteomontana.android.ui.theme.scoreColor
import com.meteomontana.android.ui.theme.scoreTextColor
import java.time.LocalDate

private val DAY_LABELS = mapOf(
    "MONDAY" to "LUN", "TUESDAY" to "MAR", "WEDNESDAY" to "MIÉ",
    "THURSDAY" to "JUE", "FRIDAY" to "VIE", "SATURDAY" to "SÁB", "SUNDAY" to "DOM"
)

@Composable
fun FavoritesGridTable(grid: FavoritesGrid, modifier: Modifier = Modifier) {
    if (grid.rows.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("FAVORITOS · 7 DÍAS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Header row con días
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Box(modifier = Modifier.width(80.dp))
            grid.days.forEach { date ->
                val label = labelForDate(date)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Filas escuelas
        grid.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.schoolName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.width(80.dp).padding(end = 4.dp),
                    maxLines = 1
                )
                row.cells.take(grid.days.size).forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.dp)
                            .height(28.dp)
                            .background(scoreColor(cell.avgScore), RoundedCornerShape(2.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(cell.avgScore.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = scoreTextColor(cell.avgScore))
                    }
                }
            }
        }
    }
}

private fun labelForDate(iso: String): String = try {
    val d = LocalDate.parse(iso)
    DAY_LABELS[d.dayOfWeek.name] ?: d.dayOfWeek.name.take(3)
} catch (_: Throwable) { iso.takeLast(2) }
