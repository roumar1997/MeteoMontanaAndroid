package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteomontana.android.data.api.MountainBulletinDto
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra

/**
 * Boletín de montaña oficial de AEMET — solo aparece si la escuela cae en
 * uno de los 9 macizos con boletín (Guadarrama, Gredos, Pirineos...).
 * Plegado por defecto: cabecera con el macizo; al abrir, los textos del
 * meteorólogo + atmósfera libre + temperaturas por cotas.
 */
@Composable
fun MountainBulletinSection(bulletin: MountainBulletinDto) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Column(Modifier.weight(1f)) {
                Text("BOLETÍN DE MONTAÑA · AEMET", style = EyebrowTextStyle, color = Terra)
                Text(bulletin.areaName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Column(
                Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                BulletinRow("CIELO", bulletin.texts["nubosidad"])
                BulletinRow("PRECIPITACIONES", bulletin.texts["pcp"])
                BulletinRow("TORMENTAS", bulletin.texts["tormentas"], highlight = true)
                BulletinRow("TEMPERATURAS", bulletin.texts["temperatura"])
                BulletinRow("VIENTO", bulletin.texts["viento"])

                val atmo = listOfNotNull(
                    bulletin.texts["isocero"]?.let { "Isoterma 0° a $it" },
                    bulletin.texts["v1500"]?.let { "Viento a 1.500 m: $it" },
                    bulletin.texts["v3000"]?.let { "Viento a 3.000 m: $it" })
                if (atmo.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("EN ALTURA", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    atmo.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (bulletin.spots.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("TEMPERATURAS POR COTAS", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    bulletin.spots.forEach { spot ->
                        Row {
                            Text("${spot.nombre} (${spot.altitud})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("${spot.minima}° / ${spot.maxima}°",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Text("Fuente: AEMET",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BulletinRow(label: String, text: String?, highlight: Boolean = false) {
    if (text.isNullOrBlank()) return
    Column {
        Text(label, style = EyebrowTextStyle,
            color = if (highlight && !text.startsWith("No se esperan")) Terra
                    else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
