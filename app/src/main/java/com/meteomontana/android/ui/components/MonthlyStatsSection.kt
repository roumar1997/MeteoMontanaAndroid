package com.meteomontana.android.ui.components


import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.util.AppText
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
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

@Composable
fun MonthlyStatsSection(stats: MonthlyStats?, isLoading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Text(
            stringResource(R.string.monthly_stats_section_v2_indice_por_mes_ult_3),
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
                Text(stringResource(R.string.monthly_stats_section_mejor_temporada),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = Mono, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.secondary)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(com.meteomontana.android.util.ForecastText.monthsIn(range),
                    style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onBackground)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.sm))
        }
        val names = CalendarLabels.monthsShort()
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
    score >= 80 -> AppText.get(R.string.w_excellent)
    score >= 65 -> AppText.get(R.string.w_good)
    score >= 50 -> AppText.get(R.string.w_fair)
    score >= 30 -> AppText.get(R.string.w_poor)
    else        -> AppText.get(R.string.monthly_stats_section_v4_muy_malo)
}

