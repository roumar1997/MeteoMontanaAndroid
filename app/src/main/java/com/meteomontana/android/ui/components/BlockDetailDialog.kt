package com.meteomontana.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.gradeStyle

/**
 * Dialog con detalles de un bloque/parking/zona seleccionado en el mapa:
 * foto con líneas (solo BLOCK con foto), lista de vías, descripción, coords
 * y botón "CÓMO LLEGAR" (abre Google Maps).
 *
 * Compartido entre el mapa del admin (`FullScreenMapDialog`) y el mapa del
 * usuario en `SchoolMap`. Mismo render para ambos.
 *
 * @param isProposal si es la propuesta pendiente (cambia el badge y color).
 */
@Composable
fun BlockDetailDialog(
    block: Block,
    isProposal: Boolean = false,
    onAddLines: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                val (badgeColor, badgeLabel) = when {
                    isProposal              -> Color(0xFFF59E0B) to "PROPUESTA"
                    block.type == "PARKING" -> Color(0xFF1D6DD6) to "PARKING"
                    block.type == "ZONE"    -> Color(0xFF1FA84E) to "ZONA"
                    else                    -> Terra to "PIEDRA"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(badgeColor)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    Text(badgeLabel, style = EyebrowTextStyle, color = Color.White)
                }
                Text(
                    block.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    Text("✕", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Foto con líneas (solo BLOCK con photoPath)
            val photoPath = block.photoPath
            if (block.type == "BLOCK" && !photoPath.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                TopoPhotoCanvas(
                    photoUrl = photoPath,
                    lines = block.lines.toTopoLines()
                )
            }

            // Lista de vías
            if (block.type == "BLOCK" && block.lines.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "VÍAS (${block.lines.size})",
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                block.lines.forEachIndexed { idx, line ->
                    val lineGrade = line.grade
                    val style = gradeStyle(lineGrade)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(style.stroke)
                                .padding(2.dp)
                                .height(18.dp)
                        ) {
                            Text(
                                "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (style.dark) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                        if (lineGrade != null) {
                            Text(
                                lineGrade,
                                style = MaterialTheme.typography.labelMedium,
                                color = style.stroke
                            )
                        }
                        if (line.startType != null) {
                            Text(
                                "· ${line.startType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (line.name.isNotBlank()) {
                            Text(
                                line.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Descripción
            block.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }

            // Coordenadas
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "%.6f, %.6f".format(block.lat, block.lon),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.md))

            // Botón CÓMO LLEGAR (Google Maps) — disponible para cualquier tipo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Terra)
                    .clickable {
                        val uri = Uri.parse(
                            "https://www.google.com/maps/dir/?api=1&destination=${block.lat},${block.lon}"
                        )
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text("→ CÓMO LLEGAR", style = EyebrowTextStyle, color = Color.White)
            }

            // Botón "+ AÑADIR VÍAS" — solo para BLOCK y si el caller lo permite
            if (onAddLines != null && block.type == "BLOCK" && !isProposal) {
                Spacer(Modifier.height(Spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, Terra, RoundedCornerShape(2.dp))
                        .clickable(onClick = onAddLines)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ AÑADIR VÍAS", style = EyebrowTextStyle, color = Terra)
                }
            }

            // Botón "EDITAR" — admin: mover posición, renombrar, editar líneas
            if (onEdit != null && !isProposal) {
                Spacer(Modifier.height(Spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(2.dp))
                        .clickable(onClick = onEdit)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✎ EDITAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onBackground)
                }
            }

            // Botón "BORRAR" — solo si el caller lo permite (admins) y no es propuesta
            if (onDelete != null && !isProposal) {
                Spacer(Modifier.height(Spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                        .clickable { showDeleteConfirm = true }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗑 BORRAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // Dialog de confirmación de borrado
    if (showDeleteConfirm && onDelete != null) {
        val typeLabel = when (block.type) {
            "PARKING" -> "parking"
            "ZONE"    -> "sector"
            else      -> "piedra"
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
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
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("SÍ, BORRAR", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}
