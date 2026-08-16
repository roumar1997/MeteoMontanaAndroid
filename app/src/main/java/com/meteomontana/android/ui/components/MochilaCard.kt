package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Celda "mochila": el botón de selección de Cumbre — plano, con borde, sin
 * relleno de color. Al elegirse no se rellena: se marca con borde y texto en
 * terracota. Es el mismo lenguaje que las pestañas y el filtro del Feed.
 *
 * Vive aquí (y no privado en una pantalla) desde 2026-08-16, cuando se necesitó
 * el mismo botón para saltar entre las caras de una piedra: tener el diseño
 * copiado en dos sitios es como acaban divergiendo.
 */
@Composable
fun MochilaCard(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    CeldaMochila(selected, modifier, onClick) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** La misma celda, con un icono en vez de texto (p.ej. el trofeo del ranking). */
@Composable
fun MochilaIconCard(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    CeldaMochila(selected, modifier, onClick) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** El marco compartido: forma, borde y estado. Lo de dentro lo pone quien llama. */
@Composable
private fun CeldaMochila(
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}
