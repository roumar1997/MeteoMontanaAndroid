package com.meteomontana.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.theme.CumbrePillShape

/**
 * Chip estilo Cumbre: píldora redondeada, hairline 1dp.
 * - Selected: fondo terracota + texto blanco (paridad con iOS)
 * - No selected: fondo paper + borde rule
 *
 * Radio a píldora desde 2026-08-21 (Rodrigo): los CONTROLES se sienten más
 * "app nativa" redondeados; el contenido (cards, fichas) se queda recto a
 * propósito, ver DESIGN.md §1.10.
 */
@Composable
fun CumbreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape  = CumbrePillShape
    val bg     = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg     = if (selected) Color.White                       else MaterialTheme.colorScheme.onSurface
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(BorderStroke(1.dp, border), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // MONO, como en iOS. Los chips son datos —"50 km", "Granito", "Bloque"—
        // y allí van todos en JetBrains Mono; en Android iban con la sans
        // normal, y por eso la misma pantalla parecía de otra app al ponerlas
        // lado a lado. Es la diferencia que más cantaba en la comparación.
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = com.meteomontana.android.ui.theme.Mono
            ),
            color = fg,
            maxLines = 1
        )
    }
}
