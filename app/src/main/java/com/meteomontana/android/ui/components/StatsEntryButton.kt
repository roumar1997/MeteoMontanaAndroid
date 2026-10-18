package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.CumbreStatCardShape
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra

/**
 * El botón de ESTADÍSTICAS del perfil: el MISMO en tu perfil y en el de cualquier persona (antes dos celdas distintas,
 * «▃▅▇» y «▸»). Barritas que suben con los colores de los grados, el título y una línea que dice qué hay dentro
 * (Álvaro, 2026-10-18). Espejo de `StatsEntryLabel` (iOS).
 */
@Composable
fun StatsEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bars = listOf(0.35f to Color(0xFF9E9E94), 0.55f to Color(0xFF40A066), 0.78f to Color(0xFF2663C7), 1f to Color(0xFF8B5CF6))
    Row(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, CumbreStatCardShape)
            .border(1.dp, Terra.copy(alpha = 0.55f), CumbreStatCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.width(40.dp).height(30.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bars.forEach { (h, c) -> androidx.compose.foundation.layout.Box(Modifier.width(7.dp).height((30 * h).dp).clip(RoundedCornerShape(2.dp)).background(c)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.profile_screen_v3_estadisticas), style = EyebrowTextStyle.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.stats_entry_sub), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Text("›", color = Terra, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
