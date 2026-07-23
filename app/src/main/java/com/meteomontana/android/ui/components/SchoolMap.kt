package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.screens.detail.SchoolDetailViewModel
import com.meteomontana.android.ui.screens.detail.ProposeContributionFlow
import com.meteomontana.android.ui.screens.detail.AddLinesFlow
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.meteomontana.android.domain.util.Geo

/**
 * Mapa colapsable de la escuela con botón "+ PROPONER".
 *
 * @param viewModel  necesario para enviar propuestas al back.
 * @param onMyProposals  navega a la pantalla "Mis propuestas".
 */
@Composable
fun SchoolMap(
    centerLat: Double,
    centerLon: Double,
    blocks: List<Block>,
    viewModel: SchoolDetailViewModel,
    onMyProposals: () -> Unit = {},
    schoolName: String = "",
    schoolId: String = "",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // ── Ficha de piedra IZADA a este nivel (no dentro del mapa expandido) ──
    // Antes vivía en InnerMap: abrir una piedra por deep-link (feed/diario)
    // exigía expandir el mapa y arrancar MapLibre (segundos en móviles lentos).
    // Ahora la ficha abre directamente en cuanto hay bloques; el mapa solo se
    // expande cuando el usuario lo abre (o al trazar un muro desde el editor).
    var selectedBlock by remember { mutableStateOf<Block?>(null) }
    // Vía objetivo del deep-link del diario → el detalle abre por su foto/cara.
    var highlightVia by remember { mutableStateOf<String?>(null) }
    // Tick pendiente de confirmar (hoja "Publicar en el feed").
    var pendingTick by remember { mutableStateOf<PendingTick?>(null) }
    // Estado del editor de piedra/muro, agrupado (antes 9 variables sueltas).
    val wallEdit = remember { WallEditState() }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Deep-link por id de PIEDRA (post "piedra nueva" del feed, sin vía):
    // abre la ficha directamente, SIN expandir el mapa.
    val autoOpenBlockId by viewModel.autoOpenBlockId.collectAsState()
    androidx.compose.runtime.LaunchedEffect(blocks, autoOpenBlockId) {
        val blockId = autoOpenBlockId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val target = blocks.firstOrNull { it.id == blockId } ?: return@LaunchedEffect
        selectedBlock = target
        viewModel.consumeAutoOpenBlock()
    }
    // Deep-link por vía (diario, buscador, enlaces): abre la piedra que la
    // contiene. Preferimos el id ESTABLE; si no, por nombre.
    val autoOpenVia by viewModel.autoOpenVia.collectAsState()
    val autoOpenViaId by viewModel.autoOpenViaId.collectAsState()
    androidx.compose.runtime.LaunchedEffect(blocks, autoOpenVia, autoOpenViaId) {
        val viaId = autoOpenViaId?.takeIf { it.isNotBlank() }
        val via = autoOpenVia?.takeIf { it.isNotBlank() }
        if (viaId == null && via == null) return@LaunchedEffect
        if (blocks.isEmpty()) return@LaunchedEffect
        val byId = viaId?.let { id ->
            blocks.firstOrNull { b -> b.lines.any { it.id == id } }
        }
        val target = byId
            ?: via?.let { v -> blocks.firstOrNull { b -> b.lines.any { it.name.equals(v, ignoreCase = true) } } }
            ?: via?.let { v -> blocks.firstOrNull { it.name.equals(v, ignoreCase = true) } }
        if (target != null) {
            selectedBlock = target
            highlightVia = byId?.lines?.firstOrNull { it.id == viaId }?.name ?: via
            viewModel.consumeAutoOpenVia()
        }
    }

    // Puente mapa ↔ flujo de propuestas (antes: 7 estados + 5 callbacks sueltos).
    val bridge = remember { ProposalMapBridge() }

    Column(modifier = modifier.fillMaxWidth()) {

        // ── Toggle "MAPA DE LA ESCUELA" ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Outlined.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(stringResource(R.string.detail_school_map),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = EyebrowTextStyle)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text("${blocks.size} elementos",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    Text(if (expanded) "▲" else "▼",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (expanded) {
            // Buscador de vías/bloques de ESTA escuela: solo con el mapa
            // abierto (como iOS). Elegir un resultado abre su piedra.
            SchoolViaSearchBar(blocks = blocks, viewModel = viewModel)
            SchoolMapView(
                centerLat     = centerLat,
                centerLon     = centerLon,
                blocks        = blocks,
                schoolName    = schoolName,
                schoolId      = schoolId,
                viewModel     = viewModel,
                bridge        = bridge,
                wallEdit      = wallEdit,
                // Ficha de piedra izada a ESTE nivel (deep-links sin MapLibre).
                onBlockSelected = { selectedBlock = it },
                onDismissBlock = { selectedBlock = null }
            )
        }
    }

    // ── Flujo de propuesta (dialogs) ──────────────────────────────────────
    if (bridge.proposeOpen) {
        ProposeContributionFlow(
            schoolName      = schoolName,
            schoolLat       = centerLat,
            schoolLon       = centerLon,
            waitingForTap   = bridge.waitingMapTap,
            onStartWaitingTap = { bridge.waitingMapTap = true },
            onMapTap        = { cb -> bridge.mapTapCallback = cb },
            onMarkerTapForCorrection = { cb -> bridge.markerTapForCorrection = cb },
            onCorrectionModeChange = { bridge.correctionMode = it },
            onGhostMarkerChange = { bridge.correctionGhost = it },
            onCorrectionTargetChange = { bridge.correctionTargetName = it },
            onAcceptCorrection = { cb -> bridge.acceptCorrectionCallback = cb },
            onWallTracingChange = { bridge.wallTracing = it },
            onWallPreviewChange = { bridge.wallPreview = it },
            onWallUndo = { cb -> bridge.wallUndoCallback = cb },
            onWallDone = { cb -> bridge.wallDoneCallback = cb },
            onDismiss       = { bridge.reset() },
            onMyProposals   = onMyProposals,
            viewModel       = viewModel
        )
    }

    // ── Ficha de la piedra + editor + hoja de publicar (IZADOS del mapa) ────
    // Viven aquí (no dentro del mapa expandido) para que los deep-links abran
    // la ficha sin arrancar MapLibre. Los taps de marker del mapa llegan por
    // onBlockSelected; el trazado de muro del editor expande el mapa.
    val fichaCtx = LocalContext.current
    val fichaIsAdmin = (viewModel.uiState.collectAsState().value
        as? com.meteomontana.android.ui.screens.detail.SchoolDetailUiState.Success)?.isCurrentUserAdmin == true

    selectedBlock?.let { block ->
        val sectors = blocks.filter { it.type == "ZONE" }
        // Vías ya hechas (diario + cola offline) → ✓ al abrir; PROYECTO igual.
        // La traducción claves→ids vive en matchedLineIds (pura y testeada).
        val doneKeys by viewModel.doneViaKeys.collectAsState()
        val doneLineIds = remember(block, doneKeys) {
            com.meteomontana.android.ui.screens.detail.matchedLineIds(block, doneKeys)
        }
        val projectKeys by viewModel.projectViaKeys.collectAsState()
        val projectLineIds = remember(block, projectKeys) {
            com.meteomontana.android.ui.screens.detail.matchedLineIds(block, projectKeys)
        }
        BlockDetailDialog(
            block = block,
            schoolName = schoolName,
            highlightVia = highlightVia,
            initiallyTicked = doneLineIds,
            initiallyProjects = projectLineIds,
            onAddLines = if (block.type == "BLOCK") ({
                // openFor puebla el estado ANTES de abrir (sin frame vacío).
                // NO cerramos la ficha: el editor abre ENCIMA (su scrim tapa la
                // ficha) → sin parpadeo del mapa entre diálogos.
                wallEdit.openFor(block)
            }) else null,
            onEditLine = if (block.type == "BLOCK") ({ line ->
                wallEdit.editingLine = block to line
            }) else null,
            onRateLine = if (block.type == "BLOCK") ({ lineId, stars ->
                viewModel.viewModelScope.launch {
                    if (stars > 0) viewModel.rateLine(block.id, lineId, stars)
                    else viewModel.unrateLine(block.id, lineId)
                }
            }) else null,
            onTickLine = if (block.type == "BLOCK") ({ line, idx, nowDone ->
                // nowDone = lo que el usuario VE tras pulsar (estado deseado).
                // Decidir por él, no por doneLineIds: el diario puede llegar
                // tarde a la ficha y divergir del ✓ visual (borraba entradas).
                val sectorName = sectors.firstOrNull { it.id == block.sectorBlockId }?.name
                if (!nowDone) {
                    // DESMARCAR: toggle directo, sin diálogo (como siempre).
                    viewModel.viewModelScope.launch {
                        viewModel.toggleLine(block, line, idx, schoolName, sectorName, markDone = false)
                    }
                } else {
                    val wasProject = projectLineIds.contains(line.id)
                    when (com.meteomontana.android.data.local.FeedPublishPrefs.get(fichaCtx)) {
                        com.meteomontana.android.data.local.FeedPublishMode.ASK ->
                            pendingTick = PendingTick(
                                block = block, line = line, index = idx,
                                schoolName = schoolName, sectorName = sectorName,
                                wasProject = wasProject
                            )
                        com.meteomontana.android.data.local.FeedPublishMode.ALWAYS ->
                            viewModel.viewModelScope.launch {
                                val r = viewModel.toggleLine(block, line, idx, schoolName, sectorName, markDone = true)
                                if (r.getOrNull() == true) {
                                    viewModel.publishTickToFeed(block, line, wasProject)
                                }
                            }
                        com.meteomontana.android.data.local.FeedPublishMode.NEVER ->
                            viewModel.viewModelScope.launch {
                                viewModel.toggleLine(block, line, idx, schoolName, sectorName, markDone = true)
                            }
                    }
                }
            }) else null,
            onToggleProject = if (block.type == "BLOCK") ({ line, idx, nowProject ->
                val sectorName = sectors.firstOrNull { it.id == block.sectorBlockId }?.name
                viewModel.viewModelScope.launch {
                    viewModel.toggleProject(block, line, idx, schoolName, sectorName, markProject = nowProject)
                }
            }) else null,
            availableSectors = sectors.takeIf { it.isNotEmpty() },
            onAssignSector = if (block.type == "BLOCK" && sectors.isNotEmpty()) ({ sectorId ->
                selectedBlock = null
                viewModel.viewModelScope.launch {
                    val r = viewModel.submitAssignSectorContribution(
                        targetBlockId = block.id,
                        targetLat = block.lat,
                        targetLon = block.lon,
                        sectorBlockId = sectorId
                    )
                    successMessage = if (r.isSuccess)
                        if (fichaIsAdmin) "Publicado en el mapa." else "Propuesta enviada. Un admin la revisará en 24-48h."
                    else
                        "No se pudo enviar la propuesta: ${r.exceptionOrNull()?.message ?: "error"}"
                }
            }) else null,
            onDelete = if (fichaIsAdmin) ({
                val id = block.id
                selectedBlock = null
                viewModel.deleteBlock(id) {}
            }) else null,
            onDismiss = { selectedBlock = null; highlightVia = null }
        )
    }

    // Hoja de publicar el tick (estilo Cumbre).
    pendingTick?.let { pt ->
        FeedPublishSheet(
            lineLabel = pt.line.name.ifBlank { "Vía ${pt.index + 1}" } +
                (pt.line.grade?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            wasProject = pt.wasProject,
            onPublish = { always, caption, photoUri ->
                if (always) com.meteomontana.android.data.local.FeedPublishPrefs.set(
                    fichaCtx, com.meteomontana.android.data.local.FeedPublishMode.ALWAYS)
                pendingTick = null
                viewModel.viewModelScope.launch {
                    val r = viewModel.toggleLine(
                        pt.block, pt.line, pt.index, pt.schoolName, pt.sectorName, markDone = true)
                    if (r.getOrNull() == true) {
                        viewModel.publishTickToFeed(
                            pt.block, pt.line, pt.wasProject, caption,
                            photoUri = photoUri?.toString(),
                            onPhotoUploadFailed = {
                                android.widget.Toast.makeText(
                                    fichaCtx, R.string.feed_photo_upload_failed,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            },
            onDiaryOnly = {
                pendingTick = null
                viewModel.viewModelScope.launch {
                    viewModel.toggleLine(pt.block, pt.line, pt.index, pt.schoolName, pt.sectorName, markDone = true)
                }
            },
            onDismiss = { pendingTick = null }
        )
    }

    // Flujo "+ AÑADIR VÍAS" / editar piedra-muro. Se oculta mientras se traza el
    // muro en el mapa (el estado vive en wallEdit, así no se pierde lo editado).
    wallEdit.target?.let { block ->
        if (!wallEdit.tracing) {
            AddLinesFlow(
                block = block,
                viewModel = viewModel,
                faces = wallEdit.faces,
                onFacesChange = { wallEdit.faces = it },
                selectedFace = wallEdit.selectedFace,
                onSelectedFaceChange = { wallEdit.selectedFace = it },
                geometry = wallEdit.geometry,
                onGeometryChange = { wallEdit.geometry = it },
                direction = wallEdit.direction,
                onDirectionChange = { wallEdit.direction = it },
                tracedPath = wallEdit.tracedPath,
                onTraceWall = {
                    wallEdit.startTracing()
                    selectedBlock = null  // deja ver el mapa para trazar
                    expanded = true       // trazar exige el mapa abierto
                },
                onDismiss = { wallEdit.target = null; selectedBlock = null },
                onSuccess = {
                    wallEdit.target = null
                    selectedBlock = null
                    successMessage = if (fichaIsAdmin) "Publicado en el mapa." else "Propuesta enviada. Un admin la revisará en 24-48h."
                }
            )
        }
    }

    // Flujo "✎ CORREGIR VÍA" — redibuja una línea concreta
    wallEdit.editingLine?.let { (block, line) ->
        com.meteomontana.android.ui.screens.detail.EditLineFlow(
            block = block,
            line = line,
            viewModel = viewModel,
            onDismiss = { wallEdit.editingLine = null; selectedBlock = null },
            onSuccess = {
                wallEdit.editingLine = null
                selectedBlock = null
                successMessage = if (fichaIsAdmin) "Publicado en el mapa." else "Propuesta enviada. Un admin la revisará en 24-48h."
            }
        )
    }

    // Aviso de éxito tras enviar la propuesta.
    if (successMessage != null) {
        CumbreSuccessDialog(
            onClose = { successMessage = null },
            onMyProposals = {
                successMessage = null
                onMyProposals()
            }
        )
    }
}
