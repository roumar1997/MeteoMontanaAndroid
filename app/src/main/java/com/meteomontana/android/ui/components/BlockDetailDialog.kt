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
import androidx.compose.runtime.mutableStateListOf
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
    /** Vía objetivo (deep-link del diario): su cara/foto se muestra la primera. */
    highlightVia: String? = null,
    isProposal: Boolean = false,
    onAddLines: (() -> Unit)? = null,
    onEditLine: ((com.meteomontana.android.domain.model.BlockLine) -> Unit)? = null,
    /** Marca una vía como hecha (la suma al diario). null = no mostrar el tic. */
    onTickLine: ((com.meteomontana.android.domain.model.BlockLine, Int) -> Unit)? = null,
    /** Ids de vías ya hechas (del diario) para mostrarlas marcadas ✓ al abrir. */
    initiallyTicked: Set<String> = emptySet(),
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    /** Sectores (ZONE) disponibles para "ASIGNAR SECTOR". null = no mostrar el botón. */
    availableSectors: List<Block>? = null,
    onAssignSector: ((sectorBlockId: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var showLinePicker by remember { mutableStateOf(false) }
    val tickedLines = remember { mutableStateListOf<String>().apply { addAll(initiallyTicked) } }   // vías hechas
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSectorPicker by remember { mutableStateOf(false) }

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

            // Sector actual de la piedra (si lo tiene)
            if (block.type == "BLOCK" && block.sectorBlockId != null) {
                Spacer(Modifier.height(Spacing.sm))
                val sectorName = availableSectors
                    ?.firstOrNull { it.id == block.sectorBlockId }?.name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF1FA84E))
                            .padding(horizontal = Spacing.sm, vertical = 2.dp)
                    ) {
                        Text("SECTOR", style = EyebrowTextStyle, color = Color.White)
                    }
                    Text(
                        sectorName ?: "(sector desconocido)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // CARAS: una piedra grande se enseña con varias fotos. Cada cara es
            // una foto con sus vías dibujadas y, debajo, su lista de vías
            // marcables. Se hace scroll de cara en cara. Una piedra de una sola
            // foto tiene una única cara (idéntico a antes).
            if (block.type == "BLOCK") {
                // Si venimos de pulsar una vía (deep-link del diario), su foto/cara
                // va primero para "llevar a la foto correspondiente".
                val orderedFaces = block.facesOrDerived().let { fs ->
                    val viaName = highlightVia?.trim()
                    if (viaName.isNullOrBlank()) fs
                    else {
                        val hit = fs.indexOfFirst { f ->
                            f.lines.any { it.name.trim().equals(viaName, ignoreCase = true) }
                        }
                        if (hit > 0) listOf(fs[hit]) + fs.filterIndexed { i, _ -> i != hit } else fs
                    }
                }
                orderedFaces.forEachIndexed { faceIdx, face ->
                    val facePhoto = face.photoPath
                    if (!facePhoto.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.sm))
                        if (orderedFaces.size > 1) {
                            Text(
                                "FOTO ${faceIdx + 1}",
                                style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Spacing.xs))
                        }
                        TopoPhotoCanvas(
                            photoUrl = facePhoto,
                            lines = face.lines.toTopoLines()
                        )
                    }
                    if (face.lines.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "VÍAS (${face.lines.size})",
                            style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        face.lines.forEachIndexed { idx, line ->
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
                                        color = if (style.dark) MaterialTheme.colorScheme.onSurface else style.stroke
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
                                // Tic: marca/desmarca la vía en tu diario (toggle).
                                if (onTickLine != null && !isProposal) {
                                    Spacer(Modifier.weight(1f))
                                    val done = tickedLines.contains(line.id)
                                    Text(
                                        if (done) "✓" else "○",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (done) Color(0xFF1FA84E)
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                if (done) tickedLines.remove(line.id)
                                                else tickedLines.add(line.id)
                                                onTickLine(line, idx)
                                            }
                                            .padding(horizontal = 6.dp)
                                    )
                                }
                            }
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

            // Botón editor por cara (corregir/repintar/añadir + cambiar foto) —
            // solo para BLOCK y si el caller lo permite.
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
                    Text(if (block.lines.isEmpty()) "+ AÑADIR VÍAS" else "✎ EDITAR / CORREGIR VÍAS",
                        style = EyebrowTextStyle, color = Terra)
                }
            }

            // Botón "+ ASIGNAR / CAMBIAR SECTOR" — BLOCK si la escuela tiene algún
            // sector. Si ya tiene → "CAMBIAR SECTOR" (el picker muestra los demás;
            // si no hay otro, lo avisa). El backend sobrescribe el sector al aprobar.
            if (onAssignSector != null && block.type == "BLOCK" && !isProposal
                    && !availableSectors.isNullOrEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, Terra, RoundedCornerShape(2.dp))
                        .clickable { showSectorPicker = true }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (block.sectorBlockId == null) "+ ASIGNAR SECTOR" else "CAMBIAR SECTOR",
                        style = EyebrowTextStyle, color = Terra
                    )
                }
            }

            // Botón "✎ CORREGIR VÍA" — solo si hay líneas existentes
            if (onEditLine != null && block.type == "BLOCK" && !isProposal
                    && block.lines.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, Terra, RoundedCornerShape(2.dp))
                        .clickable { showLinePicker = true }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✎ CORREGIR VÍA", style = EyebrowTextStyle, color = Terra)
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

    // Selector de vía a corregir
    if (showLinePicker && onEditLine != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLinePicker = false },
            title = { Text("Elige la vía a corregir") },
            text = {
                Column {
                    block.lines.forEachIndexed { idx, line ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    showLinePicker = false
                                    onEditLine(line)
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text("${idx + 1}.",
                                style = MaterialTheme.typography.titleMedium)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(line.name,
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
                androidx.compose.material3.TextButton(onClick = { showLinePicker = false }) {
                    Text("CANCELAR")
                }
            }
        )
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

    // Picker de sector para "ASIGNAR SECTOR"
    if (showSectorPicker && onAssignSector != null && !availableSectors.isNullOrEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSectorPicker = false },
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
                                .clickable {
                                    showSectorPicker = false
                                    onAssignSector(sect.id)
                                }
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
                androidx.compose.material3.TextButton(onClick = { showSectorPicker = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}
