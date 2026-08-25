package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
    schoolStyle: String? = null,
    schoolId: String = "",
    /** Foto de "Enviar piedra": abre el flujo de proponer piedra ya con ella. */
    photoSeed: com.meteomontana.android.ui.screens.detail.PhotoSeed? = null,
    /** Elegir una foto para proponer una piedra SIN salir de la escuela. */
    onPickBoulderFromPhoto: (() -> Unit)? = null,
    onPhotoSeedConsumed: (() -> Unit)? = null,
    borrador: com.meteomontana.android.ui.screens.detail.BoulderDraftStore.Draft? = null,
    onGuardarBorrador: ((com.meteomontana.android.ui.screens.detail.BoulderDraftStore.Draft) -> Unit)? = null,
    onBorrarBorrador: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Va justo debajo del mapa, encima de PARKINGS (aproximaciones). */
    contenidoTrasMapa: @Composable () -> Unit = {}
) {
    // ── Ficha de piedra IZADA a este nivel (no dentro del mapa expandido) ──
    // Antes vivía en InnerMap: abrir una piedra por deep-link (feed/diario)
    // exigía expandir el mapa y arrancar MapLibre (segundos en móviles lentos).
    // Ahora la ficha abre directamente en cuanto hay bloques; el mapa solo se
    // expande cuando el usuario lo abre (o al trazar un muro desde el editor).
    var selectedBlock by remember { mutableStateOf<Block?>(null) }
    // Filtro LOCAL por GRADO (BLOCK_SEARCH_DESIGN.md §7). No persiste al salir
    // de la escuela. Vive aquí (no dentro del `run {}` de más abajo) porque lo
    // necesitan tanto la barra del mapa como la ficha de piedra (selectedBlock?.let).
    var selectedGrades by remember { mutableStateOf(setOf<String>()) }
    // Vía objetivo del deep-link del diario → el detalle abre por su foto/cara.
    var highlightVia by remember { mutableStateOf<String?>(null) }
    // Tick pendiente de confirmar (hoja "Publicar en el feed").
    var pendingTick by remember { mutableStateOf<PendingTick?>(null) }
    // Estado del editor de piedra/muro, agrupado (antes 9 variables sueltas).
    val wallEdit = remember { WallEditState() }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Deep-link por id de PIEDRA (post "piedra nueva" del feed, sin vía):
    // abre la ficha directamente, SIN expandir el mapa.
    val autoOpenBlockId by viewModel.autoOpenBlockId.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(blocks, autoOpenBlockId) {
        val blockId = autoOpenBlockId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val target = blocks.firstOrNull { it.id == blockId } ?: return@LaunchedEffect
        selectedBlock = target
        viewModel.consumeAutoOpenBlock()
    }
    // Deep-link por vía (diario, buscador, enlaces): abre la piedra que la
    // contiene. Preferimos el id ESTABLE; si no, por nombre.
    val autoOpenVia by viewModel.autoOpenVia.collectAsStateWithLifecycle()
    val autoOpenViaId by viewModel.autoOpenViaId.collectAsStateWithLifecycle()
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

    // Con foto, el flujo de proponer se abre solo: el usuario ya dijo lo que
    // queria al elegirla, y el mapa se despliega para poder colocarla.
    androidx.compose.runtime.LaunchedEffect(photoSeed) {
        if (photoSeed != null) {
            // Ya no hay nada que desplegar: el mapa esta siempre visible.
            bridge.proposeOpen = true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {

        // SIN barra de abrir/cerrar: el mapa se ve SIEMPRE.
        //
        // Decision de Rodrigo (2026-08-11) despues de una tarde entera con un
        // fallo en ese boton: dentro de una escuela no habia forma de plegar el
        // mapa —el toque llegaba a la pantalla pero no a la barra, y no se
        // llego a aislar por que—. El mapa es lo mas util de esta pantalla y en
        // iOS se entra viendolo, asi que quitar el toggle no es solo esquivar
        // el fallo: es un toque menos para todo el mundo.
        //
        // COSTE ASUMIDO: MapLibre arranca siempre al abrir una escuela, aunque
        // el usuario venga solo a mirar el tiempo. Si algun dia pesa, el sitio
        // donde volver a mirarlo es aqui.
        //
        // El fallo de fondo NO esta resuelto: ver project_bug_mapa_no_cierra.
        run {
            // Buscador de vías/bloques de ESTA escuela: solo con el mapa
            // abierto (como iOS). Elegir un resultado abre su piedra.
            SchoolViaSearchBar(blocks = blocks, viewModel = viewModel)
            // Filtro por orientación (consenso comunitario): filtra la lista
            // de marcadores del mapa. Piedras sin votos → solo en TODAS.
            val orientationVm: OrientationFilterViewModel =
                androidx.hilt.navigation.compose.hiltViewModel()
            androidx.compose.runtime.LaunchedEffect(schoolId) { orientationVm.load(schoolId) }
            val blockOrientations by orientationVm.orientations.collectAsStateWithLifecycle()
            val orientationFilter by orientationVm.selected.collectAsStateWithLifecycle()
            OrientationFilterChips(
                orientations = blockOrientations,
                selected = orientationFilter,
                onSelect = orientationVm::select
            )
            // Filtro Vía/Bloque de ESTA escuela (feature 2: school.style con
            // ambos estilos, ej. "Vía,Bloque"). Solo aporta si la escuela es de
            // AMBOS a la vez — de una sola, siempre pasaría todo. Paridad con
            // schoolHasBothStyles/styleFilterRow/matchesStyleFilter de
            // SchoolMapSection.swift (iOS).
            val schoolHasBothStyles = remember(schoolStyle) {
                (schoolStyle ?: "").split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet().size > 1
            }
            var styleFilter by remember { mutableStateOf(setOf<String>()) }
            if (schoolHasBothStyles) {
                StyleFilterRow(
                    selected = styleFilter,
                    onToggle = { opt ->
                        styleFilter = if (opt in styleFilter) styleFilter - opt else styleFilter + opt
                    }
                )
            }
            val visibleBlocks = remember(blocks, blockOrientations, orientationFilter, styleFilter) {
                val aspect = orientationFilter
                blocks.filter { b ->
                    // Parkings y zonas siempre visibles ante orientación: solo se filtran piedras/muros.
                    val passesOrientation = aspect == null || b.type != "BLOCK" || blockOrientations[b.id] == aspect
                    passesOrientation && matchesStyleFilter(b, styleFilter)
                }
            }
            // Filtro LOCAL por GRADO (BLOCK_SEARCH_DESIGN.md §7). `selectedGrades`
            // vive fuera de este `run {}` (ver arriba) — lo necesita también la
            // ficha de piedra, más abajo.
            val availableGrades = remember(visibleBlocks) {
                com.meteomontana.android.domain.util.availableGrades(visibleBlocks)
            }
            val gradeFilter = remember(visibleBlocks, selectedGrades) {
                com.meteomontana.android.domain.util.filterBlocksByGrades(visibleBlocks, selectedGrades)
            }
            GradeFilterBar(
                selectedGrades = selectedGrades,
                onSelectedGradesChange = { selectedGrades = it },
                availableGrades = availableGrades,
                result = gradeFilter,
                onSelectLine = { match ->
                    visibleBlocks.firstOrNull { it.id == match.blockId }?.let { selectedBlock = it }
                }
            )
            SchoolMapView(
                centerLat     = centerLat,
                centerLon     = centerLon,
                blocks        = visibleBlocks,
                schoolName    = schoolName,
                schoolId      = schoolId,
                startFullscreen = photoSeed != null,
                viewModel     = viewModel,
                bridge        = bridge,
                wallEdit      = wallEdit,
                contenidoTrasMapa = contenidoTrasMapa,
                // Ficha de piedra izada a ESTE nivel (deep-links sin MapLibre).
                onBlockSelected = { selectedBlock = it },
                onDismissBlock = { selectedBlock = null },
                gradeDimmedBlockIds = if (selectedGrades.isEmpty()) emptySet() else
                    visibleBlocks.filter { it.type == "BLOCK" && it.id !in gradeFilter.matchingBlockIds }
                        .map { it.id }.toSet()
            )
            

        }
    }

    // ── Flujo de propuesta (dialogs) ──────────────────────────────────────
    if (bridge.proposeOpen) {
        ProposeContributionFlow(
            schoolName      = schoolName,
            schoolStyle     = schoolStyle,
            schoolId        = schoolId,
            photoSeed       = photoSeed,
            onPickBoulderFromPhoto = onPickBoulderFromPhoto,
            onPhotoSeedConsumed = onPhotoSeedConsumed,
            borrador = borrador,
            onGuardarBorrador = onGuardarBorrador,
            onBorrarBorrador = onBorrarBorrador,
            onPhotoConfirmChange = { punto, aceptar, mover ->
                bridge.photoConfirm = punto
                bridge.photoAccept = aceptar
                bridge.photoMove = mover
                // El marcador fantasma es lo que hace visible DÓNDE cae.
                bridge.correctionGhost = punto?.let {
                    com.meteomontana.android.ui.screens.detail.CorrectionGhost(
                        originalId = "__FOTO__", newLat = it.first, newLon = it.second)
                }
            },
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
    // "Guardar y terminar luego" al editar una piedra (Rodrigo, 2026-08-21).
    var borradorEncontrado by remember {
        mutableStateOf<com.meteomontana.android.ui.screens.detail.EditBlockDraftStore.Draft?>(null)
    }
    val fichaIsAdmin = (viewModel.uiState.collectAsStateWithLifecycle().value
        as? com.meteomontana.android.ui.screens.detail.SchoolDetailUiState.Success)?.isCurrentUserAdmin == true

    // Filtro por grado (BLOCK_SEARCH_DESIGN.md §7): qué vías caen en la
    // selección, para atenuar el resto DENTRO de la ficha (no solo en el mapa).
    val gradeFilter = remember(blocks, selectedGrades) {
        com.meteomontana.android.domain.util.filterBlocksByGrades(blocks, selectedGrades)
    }

    selectedBlock?.let { block ->
        val sectors = blocks.filter { it.type == "ZONE" }
        // Vías ya hechas (diario + cola offline) → ✓ al abrir; PROYECTO igual.
        // La traducción claves→ids vive en matchedLineIds (pura y testeada).
        val doneKeys by viewModel.doneViaKeys.collectAsStateWithLifecycle()
        val doneLineIds = remember(block, doneKeys) {
            com.meteomontana.android.ui.screens.detail.matchedLineIds(block, doneKeys)
        }
        val projectKeys by viewModel.projectViaKeys.collectAsStateWithLifecycle()
        val projectLineIds = remember(block, projectKeys) {
            com.meteomontana.android.ui.screens.detail.matchedLineIds(block, projectKeys)
        }
        BlockDetailDialog(
            block = block,
            schoolName = schoolName,
            highlightVia = highlightVia,
            initiallyTicked = doneLineIds,
            initiallyProjects = projectLineIds,
            gradeMatchingLineIds = if (selectedGrades.isEmpty()) null else gradeFilter.matchingLineIds,
            onAddLines = if (block.type == "BLOCK") ({
                // openFor puebla el estado ANTES de abrir (sin frame vacío).
                // Y se CIERRA la ficha de la piedra: dejándola abierta debajo,
                // al arrastrar el editor asomaba por detrás y parecía roto
                // (Álvaro, 2026-08-24). En iOS una hoja sustituye a la otra.
                wallEdit.openFor(block)
                selectedBlock = null
                // ¿Había algo a medias de la última vez que se cerró sin enviar?
                com.meteomontana.android.ui.screens.detail.EditBlockDraftStore.load(fichaCtx, block.id)
                    ?.let { borradorEncontrado = it }
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
            onPublish = { always, caption, photoUri, sessionDate, aVista, alFlash ->
                if (always) com.meteomontana.android.data.local.FeedPublishPrefs.set(
                    fichaCtx, com.meteomontana.android.data.local.FeedPublishMode.ALWAYS)
                pendingTick = null
                viewModel.viewModelScope.launch {
                    val r = viewModel.toggleLine(
                        pt.block, pt.line, pt.index, pt.schoolName, pt.sectorName, markDone = true,
                        sessionDate = sessionDate, aVista = aVista, alFlash = alFlash)
                    if (r.getOrNull() == true) {
                        viewModel.publishTickToFeed(
                            pt.block, pt.line, pt.wasProject, caption,
                            photoUri = photoUri?.toString(),
                            onPublishFailed = {
                                android.widget.Toast.makeText(
                                    fichaCtx,
                                    "No se pudo publicar el ascenso (queda en tu diario)",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            },
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
            onDiaryOnly = { sessionDate, aVista, alFlash ->
                pendingTick = null
                viewModel.viewModelScope.launch {
                    viewModel.toggleLine(pt.block, pt.line, pt.index, pt.schoolName, pt.sectorName,
                        markDone = true, sessionDate = sessionDate, aVista = aVista, alFlash = alFlash)
                }
            },
            onDismiss = { pendingTick = null }
        )
    }

    // Flujo "+ AÑADIR VÍAS" / editar piedra-muro. Se oculta mientras se traza el
    // muro en el mapa (el estado vive en wallEdit, así no se pierde lo editado).
    // "Guardar y terminar luego" (Rodrigo, 2026-08-21): ya existía al crear una
    // piedra nueva, faltaba al editar una existente.
    var preguntandoGuardarEdicion by remember { mutableStateOf(false) }
    borradorEncontrado?.let { borrador ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { borradorEncontrado = null },
            title = { Text("Tienes cambios sin enviar") },
            text = { Text("Dejaste esta piedra a medias de editar. ¿Sigues donde lo dejaste o empiezas de cero?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    // Del borrador se recuperan SOLO las VÍAS y las fotos
                    // LOCALES (igual que iOS: EditLinesSheet.swift nunca
                    // guarda ni restaura la foto del servidor, solo
                    // faceBlocks + facePicked). La foto existente SIEMPRE
                    // sale de la piedra EN VIVO — nunca del borrador: la
                    // guardada es una URL FIRMADA que caduca (~1h). ANTES
                    // había un `?: cara.existingPhotoPath` de respaldo que,
                    // si el índice no coincidía, volvía a colar esa URL
                    // caducada — la foto se veía negra sin foto para
                    // sustituirla (Álvaro, 2026-08-25).
                    val actuales = wallEdit.target?.let {
                        com.meteomontana.android.ui.screens.detail.initialEditFaces(it)
                    }.orEmpty()
                    wallEdit.faces = borrador.faces.mapIndexed { i, cara ->
                        cara.copy(existingPhotoPath = actuales.getOrNull(i)?.existingPhotoPath)
                    }
                    borradorEncontrado = null
                }) { Text("CONTINUAR EDITANDO") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.meteomontana.android.ui.screens.detail.EditBlockDraftStore.clear(fichaCtx, borrador.blockId)
                    borradorEncontrado = null
                }) { Text("DESCARTAR") }
            }
        )
    }
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
                },
                onDismiss = {
                    if (com.meteomontana.android.ui.screens.detail.EditBlockDraftStore.tieneContenido(wallEdit.faces)) {
                        preguntandoGuardarEdicion = true
                    } else {
                        wallEdit.target = null; selectedBlock = null
                    }
                },
                onSuccess = {
                    com.meteomontana.android.ui.screens.detail.EditBlockDraftStore.clear(fichaCtx, block.id)
                    wallEdit.target = null
                    selectedBlock = null
                    successMessage = if (fichaIsAdmin) "Publicado en el mapa." else "Propuesta enviada. Un admin la revisará en 24-48h."
                }
            )
        }
    }
    if (preguntandoGuardarEdicion) {
        wallEdit.target?.let { block ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { preguntandoGuardarEdicion = false },
                title = { Text("¿Guardar para terminar luego?") },
                text = { Text("Se queda guardado en este móvil. No se envía a nadie hasta que lo termines.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        com.meteomontana.android.ui.screens.detail.EditBlockDraftStore.save(fichaCtx, block.id, wallEdit.faces)
                        preguntandoGuardarEdicion = false
                        wallEdit.target = null; selectedBlock = null
                    }) { Text("GUARDAR") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        com.meteomontana.android.ui.screens.detail.EditBlockDraftStore.clear(fichaCtx, block.id)
                        preguntandoGuardarEdicion = false
                        wallEdit.target = null; selectedBlock = null
                    }) { Text("DESCARTAR") }
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

// ── Filtro Vía/Bloque de la escuela ─────────────────────────────────────
// Paridad con styleFilterRow/matchesStyleFilter de SchoolMapSection.swift.

@Composable
private fun StyleFilterRow(
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("Vía", "Bloque").forEach { opt ->
            val active = opt in selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) Terra else MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { onToggle(opt) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt.uppercase(), style = EyebrowTextStyle,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "ROUTE" en el backend = estilo "Vía"; "BOULDER" = "Bloque". */
private fun styleLabelForDiscipline(discipline: String): String =
    if (discipline.uppercase() == "ROUTE") "Vía" else "Bloque"

/**
 * ¿Esta piedra/sector pasa el filtro Vía/Bloque? Vacío = sin filtrar. Un
 * sector sin piedras dentro (sectorDisciplines vacío/null) siempre se ve —
 * no hay disciplina que comparar todavía (evita que desaparezca).
 */
private fun matchesStyleFilter(b: Block, styleFilter: Set<String>): Boolean {
    if (styleFilter.isEmpty()) return true
    return when (b.type.uppercase()) {
        "BLOCK" -> styleLabelForDiscipline(b.discipline) in styleFilter
        "ZONE" -> {
            val disciplines = b.sectorDisciplines ?: emptyList()
            if (disciplines.isEmpty()) true
            else disciplines.any { styleLabelForDiscipline(it) in styleFilter }
        }
        else -> true
    }
}
