package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.gradeChipColor
import com.meteomontana.android.ui.theme.gradeStyle

/**
 * Una vía PLEGADA: una sola línea con lo justo para reconocerla (número con el
 * color de su grado, nombre · grado, marca de dibujada) más editar y borrar.
 *
 * En los editores de vías solo hay UNA ficha abierta a la vez; el resto se
 * pliegan aquí. Con los formularios apilados, meter la quinta vía obligaba a
 * scrollear las cuatro anteriores (Álvaro, 2026-08-24). Espejo de `viaCompacta`
 * de EditLinesSheet.swift / BoulderFormSheet.swift.
 */
@Composable
internal fun ViaCompactaRow(
    displayNumber: Int,
    bloque: BoulderBloqueForm,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val chip = gradeChipColor(bloque.grade)
    val titulo = bloque.name.trim().ifEmpty { stringResource(R.string.via_sin_nombre) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(chip),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$displayNumber",
                style = MaterialTheme.typography.labelSmall,
                color = if (gradeStyle(bloque.grade).dark) Color.Black else Color.White
            )
        }
        Text(
            bloque.grade?.let { "$titulo · $it" } ?: titulo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (bloque.linePath.isNotEmpty()) {
            Text("✓", style = MaterialTheme.typography.labelSmall, color = Ok)
        }
        // Zonas de toque de 40dp: los iconos sueltos se fallaban al pulsar.
        Box(
            modifier = Modifier.size(40.dp).clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.via_editar),
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (onDelete != null) {
            Box(
                modifier = Modifier.size(40.dp).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.via_borrar),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}
