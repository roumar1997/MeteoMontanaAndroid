@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.gradeStyle
import kotlinx.coroutines.launch

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
    /** Nombre de la escuela (para el texto de compartir). */
    schoolName: String = "",
    /** Vía objetivo (deep-link del diario): su cara/foto se muestra la primera. */
    highlightVia: String? = null,
    isProposal: Boolean = false,
    onAddLines: (() -> Unit)? = null,
    onEditLine: ((com.meteomontana.android.domain.model.BlockLine) -> Unit)? = null,
    /** Marca/desmarca una vía en el diario. El 3er parámetro es el estado
     *  DESEADO (true = marcar hecha) — lo que el usuario ve al pulsar, para
     *  que una carga tardía del diario no invierta la acción. null = sin tic. */
    onTickLine: ((com.meteomontana.android.domain.model.BlockLine, Int, Boolean) -> Unit)? = null,
    /** Ids de vías ya hechas (del diario) para mostrarlas marcadas ✓. */
    initiallyTicked: Set<String> = emptySet(),
    /** Marca/desmarca una vía como PROYECTO (3er parámetro = estado deseado). */
    onToggleProject: ((com.meteomontana.android.domain.model.BlockLine, Int, Boolean) -> Unit)? = null,
    /** Ids de vías ya marcadas como proyecto, para mostrarlas al abrir. */
    initiallyProjects: Set<String> = emptySet(),
    /** Valorar una vía (1-5 estrellas). null = no mostrar estrellas. */
    onRateLine: ((lineId: String, stars: Int) -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    /** Sectores (ZONE) disponibles para "ASIGNAR SECTOR". null = no mostrar el botón. */
    availableSectors: List<Block>? = null,
    onAssignSector: ((sectorBlockId: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var showLinePicker by remember { mutableStateOf(false) }
    val tickedLines = remember { mutableStateListOf<String>().apply { addAll(initiallyTicked) } }   // vías hechas
    val projectLines = remember { mutableStateListOf<String>().apply { addAll(initiallyProjects) } } // vías proyecto
    // Si la ficha se abrió ANTES de que cargara el diario (la vista instantánea
    // la abre muy rápido → salía desmarcada, #14/#15), FUSIONA las marcas que
    // van llegando. Solo AÑADE por lineId (nunca quita: quitar es acción del
    // usuario). Es seguro con homónimas porque las claves ya son lineIds, no
    // nombres — el motivo por el que antes NO se sincronizaba desapareció al
    // migrar el diario a lineId.
    androidx.compose.runtime.LaunchedEffect(initiallyTicked) {
        initiallyTicked.forEach { if (!tickedLines.contains(it)) tickedLines.add(it) }
    }
    androidx.compose.runtime.LaunchedEffect(initiallyProjects) {
        initiallyProjects.forEach { if (!projectLines.contains(it)) projectLines.add(it) }
    }
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSectorPicker by remember { mutableStateOf(false) }
    // "OPCIONES" plegado: la ficha ya tiene muchos botones; solo CÓMO LLEGAR
    // queda a la vista y el resto (editar vías, sector, eliminar) va dentro.
    var optionsOpen by remember { mutableStateOf(false) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)   // tarjeta a pantalla (casi) completa, como el resto de sheets
                // Sin esto el teclado tapa el campo/botón de comentar una vía.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                // Holgura abajo para que los últimos botones (p.ej. OPCIONES
                // desplegado) queden por ENCIMA de la cápsula flotante de
                // pestañas, que ahora está siempre visible y se dibuja sobre
                // el contenido.
                .padding(bottom = 100.dp)
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
                if (onTickLine != null && !isProposal && block.lines.isNotEmpty()) {
                    com.meteomontana.android.ui.components.FirstTimeHint(
                        hintKey = "via_tick",
                        text = "Toca el círculo de una vía para apuntarla como hecha en tu diario."
                    )
                }
                if (onToggleProject != null && !isProposal && block.lines.isNotEmpty()) {
                    com.meteomontana.android.ui.components.FirstTimeHint(
                        hintKey = "via_project",
                        text = "Toca la P de una vía para marcarla como PROYECTO (la estás probando, aún no te ha salido)."
                    )
                }
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
                            "${stringResource(R.string.block_routes)} (${face.lines.size})",
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
                                        line.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                // Compartir esta vía/bloque (WhatsApp etc.): enlace que
                                // abre la app directamente en esta piedra con la línea.
                                if (!isProposal) {
                                    Spacer(Modifier.weight(1f))
                                    androidx.compose.material3.Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = "Compartir",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                val sectorName = availableSectors
                                                    ?.firstOrNull { it.id == block.sectorBlockId }?.name
                                                shareVia(shareScope, context, block, line, schoolName, tickedLines.toSet(), projectLines.toSet(), sectorName)
                                            }
                                            .padding(5.dp)
                                            .size(22.dp)
                                    )
                                }
                                // Proyecto: la estás probando, aún no te ha salido. Oculto
                                // si ya está hecha (no tiene sentido marcarla como proyecto).
                                if (onToggleProject != null && !isProposal) {
                                    val done = tickedLines.contains(line.id)
                                    if (!done) {
                                        // (El compartir ya empujó el grupo a la derecha.)
                                        val isProject = projectLines.contains(line.id)
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .then(
                                                    if (isProject) Modifier.background(Terra, CircleShape)
                                                    else Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                                                )
                                                .clickable {
                                                    if (isProject) projectLines.remove(line.id)
                                                    else projectLines.add(line.id)
                                                    onToggleProject(line, idx, !isProject)
                                                },
                                            contentAlignment = androidx.compose.ui.Alignment.Center
                                        ) {
                                            Text(
                                                "P",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isProject) Color.White
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                                // Tic: marca/desmarca la vía en tu diario (toggle).
                                if (onTickLine != null && !isProposal) {
                                    val done = tickedLines.contains(line.id)
                                    // El botón de compartir ya empujó el grupo a la derecha.
                                    if (isProposal) Spacer(Modifier.weight(1f))
                                    Text(
                                        if (done) "✓" else "○",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (done) Color(0xFF1FA84E)
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                if (done) {
                                                    tickedLines.remove(line.id)
                                                } else {
                                                    tickedLines.add(line.id)
                                                    // Al marcarla hecha desde aquí, si era un proyecto
                                                    // desaparece de la lista local (promoción; el
                                                    // ViewModel hace lo mismo en el servidor).
                                                    projectLines.remove(line.id)
                                                }
                                                onTickLine(line, idx, !done)
                                            }
                                            .padding(horizontal = 6.dp)
                                    )
                                }
                            }
                            // Estrellas: valoración media + votar (si está habilitado)
                            if (onRateLine != null && !isProposal && block.type == "BLOCK") {
                                LineStarsRow(
                                    lineId = line.id,
                                    avgStars = line.avgStars,
                                    myStars = line.myStars ?: 0,
                                    onRate = { stars -> onRateLine(line.id, stars) }
                                )
                            }
                            // Descripción/beta de la vía (si la tiene).
                            line.lineDescription?.takeIf { it.isNotBlank() }?.let { d ->
                                Text(d, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 28.dp, bottom = 2.dp))
                            }
                            // Comentarios de ESTA vía (desplegable).
                            if (!isProposal) {
                                Box(Modifier.padding(start = 28.dp)) {
                                    LineCommentsThread(
                                        blockId = block.id,
                                        lineId = line.id,
                                        myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
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

            // (Los comentarios viven en cada vía, no en la piedra entera —
            // decisión de Rodrigo 2026-07-04: evitaba el doble "COMENTARIOS".)

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
                Text("→ ${stringResource(R.string.common_directions)}", style = EyebrowTextStyle, color = Color.White)
            }

            BlockOptionsSection(
                block = block, isProposal = isProposal,
                optionsOpen = optionsOpen, onToggleOptions = { optionsOpen = !optionsOpen },
                onAddLines = onAddLines, availableSectors = availableSectors,
                onOpenSectorPicker = if (onAssignSector != null) ({ showSectorPicker = true }) else null,
                onEdit = onEdit,
                onRequestDelete = if (onDelete != null) ({ showDeleteConfirm = true }) else null
            )
        }
    }

    // Selector de via a corregir
    if (showLinePicker && onEditLine != null) {
        BlockLinePickerDialog(block,
            onPick = { showLinePicker = false; onEditLine(it) },
            onDismiss = { showLinePicker = false })
    }

    // Dialog de confirmacion de borrado
    if (showDeleteConfirm && onDelete != null) {
        BlockDeleteConfirmDialog(block,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false })
    }

    // Picker de sector para "ASIGNAR SECTOR"
    if (showSectorPicker && onAssignSector != null && !availableSectors.isNullOrEmpty()) {
        BlockSectorPickerDialog(block, availableSectors,
            onPick = { showSectorPicker = false; onAssignSector(it) },
            onDismiss = { showSectorPicker = false })
    }
}
