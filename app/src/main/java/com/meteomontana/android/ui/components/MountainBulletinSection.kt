package com.meteomontana.android.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteomontana.android.data.api.MountainBulletinDto
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra

/**
 * Boletín de montaña oficial de AEMET — solo aparece si la escuela cae en
 * uno de los 9 macizos con boletín (Guadarrama, Gredos, Pirineos...).
 *
 * Si el meteorólogo espera TORMENTAS (o chubascos), la tarjeta "se ilumina":
 * borde terra + chip de aviso visible SIN abrir el desplegable.
 */
@Composable
fun MountainBulletinSection(bulletin: MountainBulletinDto) {
    var expanded by remember { mutableStateOf(false) }
    val alert = bulletinAlert(bulletin)
    val borderColor = if (alert != null) Terra else MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (alert != null) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .animateContentSize()
    ) {
        // ── Cabecera (siempre visible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Terra.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Landscape, contentDescription = null,
                    tint = Terra, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("BOLETÍN DE MONTAÑA · AEMET", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bulletin.areaName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                // Aviso iluminado: se ve sin desplegar.
                alert?.let {
                    Text("⚠ $it", style = EyebrowTextStyle, color = Terra,
                        modifier = Modifier.padding(top = 2.dp))
                }
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
                BulletinRow("TORMENTAS", bulletin.texts["tormentas"],
                    highlight = alert != null)
                BulletinRow("TEMPERATURAS", bulletin.texts["temperatura"])
                BulletinRow("VIENTO", bulletin.texts["viento"])

                // Atmósfera libre como chips mono (isoterma y viento en altura).
                val chips = listOfNotNull(
                    bulletin.texts["isocero"]?.let { "ISO 0° · $it" },
                    bulletin.texts["v1500"]?.let { "1.500 M · $it" },
                    bulletin.texts["v3000"]?.let { "3.000 M · $it" })
                if (chips.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)) {
                        chips.forEach { AltitudeChip(it) }
                    }
                }

                if (bulletin.spots.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("TEMPERATURAS POR COTAS", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    bulletin.spots.forEach { spot ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(spot.nombre,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Text(spot.altitud,
                                style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
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

/**
 * Texto del aviso si el boletín trae algo que deba iluminarse, o null.
 * Criterio: el apartado de tormentas no dice "No se esperan" (los redactados
 * de AEMET usan esa fórmula fija cuando está limpio).
 */
private fun bulletinAlert(b: MountainBulletinDto): String? {
    val tormentas = b.texts["tormentas"] ?: return null
    if (tormentas.startsWith("No se esperan")) return null
    return "TORMENTAS: ${tormentas.removeSuffix(".").lowercase()}"
}

@Composable
private fun BulletinRow(label: String, text: String?, highlight: Boolean = false) {
    if (text.isNullOrBlank()) return
    Column {
        Text(label, style = EyebrowTextStyle,
            color = if (highlight) Terra else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AltitudeChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurface)
    }
}
