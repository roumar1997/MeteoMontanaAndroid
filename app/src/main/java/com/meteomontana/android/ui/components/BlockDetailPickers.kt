package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

// Diálogos auxiliares de la ficha de piedra (reparto del antiguo
// BlockDetailDialog.kt): elegir vía a corregir, confirmar borrado y elegir sector.

/** Selector de vía a corregir. */
@Composable
internal fun BlockLinePickerDialog(
    block: Block,
    onPick: (com.meteomontana.android.domain.model.BlockLine) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elige la vía a corregir") },
        text = {
            Column {
                block.lines.forEachIndexed { idx, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPick(line) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text("${idx + 1}.",
                            style = MaterialTheme.typography.titleMedium)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.displayName,
                                style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(line.grade, line.startType?.toString())
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/** Confirmación de borrado de un bloque (piedra/parking/sector). */
@Composable
internal fun BlockDeleteConfirmDialog(
    block: Block,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val typeLabel = when (block.type) {
        "PARKING" -> "parking"
        "ZONE"    -> "sector"
        else      -> "piedra"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Borrar este $typeLabel?") },
        text = {
            Text(
                if (block.type == "BLOCK")
                    "Se borrará \"${block.name}\" y todas sus vías. Esta acción no se puede deshacer."
                else
                    "Se borrará \"${block.name}\". Esta acción no se puede deshacer."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("SÍ, BORRAR", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/** Picker de sector para "ASIGNAR / CAMBIAR SECTOR". */
@Composable
internal fun BlockSectorPickerDialog(
    block: Block,
    availableSectors: List<Block>,
    onPick: (sectorBlockId: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir sector") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "El admin revisará la propuesta. Si se aprueba, esta piedra quedará asignada al sector elegido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                // Excluye el sector actual: solo tiene sentido cambiar a otro.
                val otherSectors = availableSectors.filter { it.id != block.sectorBlockId }
                if (otherSectors.isEmpty()) {
                    Text(
                        "Esta escuela solo tiene este sector. Crea otro con \"+ PROPONER → SECTOR\" para poder cambiarlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                otherSectors.forEach { sect ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPick(sect.id) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1FA84E))
                                .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        ) {
                            Text("ZONA", style = EyebrowTextStyle, color = Color.White)
                        }
                        Spacer(Modifier.padding(horizontal = Spacing.xs))
                        Text(sect.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
