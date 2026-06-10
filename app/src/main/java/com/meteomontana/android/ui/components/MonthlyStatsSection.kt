package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.data.stats.MonthlyStats
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.scoreColor

@Composable
fun MonthlyStatsSection(stats: MonthlyStats?, isLoading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Text(
            "ÍNDICE POR MES (ÚLT. 3 AÑOS)",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.sm))
        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(24.dp))
            }
            return
        }
        if (stats == null) return
        stats.bestRange?.let { range ->
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                Text("✓ MEJOR TEMPORADA",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = Mono, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.secondary)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(range,
                    style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onBackground)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.sm))
        }
        val names = listOf("Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic")
        stats.scores.forEachIndexed { i, score ->
            MonthBar(name = names[i], score = score)
        }
    }
}

@Composable
private fun MonthBar(name: String, score: Int) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold, fontFamily = Mono
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(36.dp))
        Box(modifier = Modifier
            .weight(1f)
            .height(10.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))) {
            Box(modifier = Modifier
                .fillMaxWidth(fraction = (score / 100f).coerceIn(0f, 1f))
                .height(10.dp)
                .background(scoreColor(score)))
        }
        Text(score.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = Mono, fontWeight = FontWeight.Bold
            ),
            color = scoreColor(score),
            modifier = Modifier.width(32.dp))
        Text(labelFor(score),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp))
    }
}

private fun labelFor(score: Int): String = when {
    score >= 80 -> "Excelente"
    score >= 65 -> "Bueno"
    score >= 50 -> "Regular"
    score >= 30 -> "Malo"
    else        -> "Muy malo"
}

