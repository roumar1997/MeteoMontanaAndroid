package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Map
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

private enum class MapStyleOption(val labelResId: Int) {
    SATELLITE(R.string.map_satellite),
    TOPO(R.string.map_topo)
}

private fun styleJsonFor(style: MapStyleOption): String = when (style) {
    MapStyleOption.SATELLITE -> """{"version":8,"sources":{"sat":{"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"Tiles © Esri"}},"layers":[{"id":"sat","type":"raster","source":"sat"}]}"""
    MapStyleOption.TOPO      -> """{"version":8,"sources":{"topo":{"type":"raster","tiles":["https://a.tile.opentopomap.org/{z}/{x}/{y}.png","https://b.tile.opentopomap.org/{z}/{x}/{y}.png","https://c.tile.opentopomap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenTopoMap (CC-BY-SA)"}},"layers":[{"id":"topo","type":"raster","source":"topo"}]}"""
}

// Si el usuario está más lejos de esto del centro de los elementos de la
// escuela, su ubicación NO entra en el encuadre inicial (evita que "verla
// desde casa" estire el zoom para abarcarte a ti también).
private const val MAX_USER_LOCATION_INCLUDE_KM = 20.0

/**
 * Encuadre inicial del mapa: TODOS los elementos de la escuela (parkings,
 * sectores, piedras), por muy separados que estén — antes era zoom fijo 15
 * centrado en la escuela, que dejaba fuera sectores a kilómetros. La
 * ubicación del usuario se incluye SOLO si ya está razonablemente cerca.
 */
private fun fitSchoolBoundsCameraUpdate(
    markers: List<Block>,
    userLoc: com.meteomontana.android.domain.model.UserLocation?,
    padding: Int = 90
): org.maplibre.android.camera.CameraUpdate? {
    if (markers.isEmpty()) return null
    val boundsBuilder = LatLngBounds.Builder()
    markers.forEach { boundsBuilder.include(LatLng(it.lat, it.lon)) }
    if (userLoc != null) {
        val avgLat = markers.map { it.lat }.average()
        val avgLon = markers.map { it.lon }.average()
        val distKm = Geo.haversineKm(userLoc.lat, userLoc.lon, avgLat, avgLon)
        if (distKm <= MAX_USER_LOCATION_INCLUDE_KM) {
            boundsBuilder.include(LatLng(userLoc.lat, userLoc.lon))
        }
    }
    return try {
        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding)
    } catch (_: Exception) {
        // Un único punto (o todos coincidentes) → bounds degenerado, cae a zoom fijo.
        val first = markers.first()
        CameraUpdateFactory.newLatLngZoom(LatLng(first.lat, first.lon), 15.0)
    }
}

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

    // Deep-link desde el diario: si hay una vía objetivo (por id o nombre), despliega el mapa.
    val autoOpenVia by viewModel.autoOpenVia.collectAsState()
    val autoOpenViaId by viewModel.autoOpenViaId.collectAsState()
    androidx.compose.runtime.LaunchedEffect(autoOpenVia, autoOpenViaId) {
        if (!autoOpenVia.isNullOrBlank() || !autoOpenViaId.isNullOrBlank()) expanded = true
    }

    // Estado del flujo de propuesta
    var proposeOpen    by remember { mutableStateOf(false) }
    var waitingMapTap  by remember { mutableStateOf(false) }
    var correctionMode by remember { mutableStateOf(false) }
    var correctionGhost by remember { mutableStateOf<com.meteomontana.android.ui.screens.detail.CorrectionGhost?>(null) }
    var correctionTargetName by remember { mutableStateOf<String?>(null) }
    // Trazado de muro: modo activo + polilínea en construcción (preview).
    var wallTracing by remember { mutableStateOf(false) }
    var wallPreview by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }

    // Callback que el flujo registra para recibir el tap en el mapa
    var mapTapCallback by remember { mutableStateOf<((Double, Double) -> Unit)?>(null) }
    // Callback que el flujo registra para recibir un tap en un marker existente.
    var markerTapForCorrection by remember { mutableStateOf<((Block) -> Unit)?>(null) }
    // Callback que el flujo registra para que se dispare al pulsar ACEPTAR.
    var acceptCorrectionCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Callbacks DESHACER / LISTO del trazado de muro.
    var wallUndoCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var wallDoneCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

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
            InnerMap(
                centerLat     = centerLat,
                centerLon     = centerLon,
                blocks        = blocks,
                schoolName    = schoolName,
                schoolId      = schoolId,
                viewModel     = viewModel,
                onMyProposals = onMyProposals,
                waitingMapTap = waitingMapTap,
                correctionMode = correctionMode,
                correctionGhost = correctionGhost,
                correctionTargetName = correctionTargetName,
                wallTracing = wallTracing,
                wallPreview = wallPreview,
                onProposeClick = { proposeOpen = true },
                onCancelTap   = {
                    waitingMapTap = false
                    correctionMode = false
                    correctionGhost = null
                    correctionTargetName = null
                    wallTracing = false
                    wallPreview = emptyList()
                    proposeOpen = false
                    mapTapCallback = null
                    markerTapForCorrection = null
                    acceptCorrectionCallback = null
                    wallUndoCallback = null
                    wallDoneCallback = null
                },
                onMapTapped   = { lat, lon ->
                    mapTapCallback?.invoke(lat, lon)
                    // No reseteamos waitingMapTap en corrección ni en trazado (siguen activos).
                    if (!correctionMode && !wallTracing) waitingMapTap = false
                },
                onMarkerTappedForCorrection = { block ->
                    markerTapForCorrection?.invoke(block)
                },
                onAcceptCorrection = { acceptCorrectionCallback?.invoke() },
                onWallUndo = { wallUndoCallback?.invoke() },
                onWallDone = { wallDoneCallback?.invoke() }
            )
        }
    }

    // ── Flujo de propuesta (dialogs) ──────────────────────────────────────
    if (proposeOpen) {
        ProposeContributionFlow(
            schoolName      = schoolName,
            schoolLat       = centerLat,
            schoolLon       = centerLon,
            waitingForTap   = waitingMapTap,
            onStartWaitingTap = { waitingMapTap = true },
            onMapTap        = { cb -> mapTapCallback = cb },
            onMarkerTapForCorrection = { cb -> markerTapForCorrection = cb },
            onCorrectionModeChange = { correctionMode = it },
            onGhostMarkerChange = { correctionGhost = it },
            onCorrectionTargetChange = { correctionTargetName = it },
            onAcceptCorrection = { cb -> acceptCorrectionCallback = cb },
            onWallTracingChange = { wallTracing = it },
            onWallPreviewChange = { wallPreview = it },
            onWallUndo = { cb -> wallUndoCallback = cb },
            onWallDone = { cb -> wallDoneCallback = cb },
            onDismiss       = {
                proposeOpen = false
                waitingMapTap = false
                correctionMode = false
                correctionGhost = null
                correctionTargetName = null
                wallTracing = false
                wallPreview = emptyList()
                mapTapCallback = null
                markerTapForCorrection = null
                acceptCorrectionCallback = null
                wallUndoCallback = null
                wallDoneCallback = null
            },
            onMyProposals   = onMyProposals,
            viewModel       = viewModel
        )
    }
}

// ─── InnerMap ─────────────────────────────────────────────────────────────────

@Composable
private fun InnerMap(
    centerLat: Double,
    centerLon: Double,
    blocks: List<Block>,
    schoolName: String,
    schoolId: String,
    viewModel: SchoolDetailViewModel,
    onMyProposals: () -> Unit,
    waitingMapTap: Boolean,
    correctionMode: Boolean,
    correctionGhost: com.meteomontana.android.ui.screens.detail.CorrectionGhost?,
    correctionTargetName: String?,
    wallTracing: Boolean,
    wallPreview: List<Pair<Double, Double>>,
    onProposeClick: () -> Unit,
    onCancelTap: () -> Unit,
    onMapTapped: (Double, Double) -> Unit,
    onMarkerTappedForCorrection: (Block) -> Unit,
    onAcceptCorrection: () -> Unit,
    onWallUndo: () -> Unit,
    onWallDone: () -> Unit
) {
    val ctx = LocalContext.current
    var currentStyle by remember { mutableStateOf(MapStyleOption.SATELLITE) }
    val mapViewRef   = remember { mutableStateOf<MapView?>(null) }
    val mapRef       = remember { mutableStateOf<MapLibreMap?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Snapshots actualizados de flags y callbacks para que los listeners del factory los lean fresh.
    val waitingMapTapState by androidx.compose.runtime.rememberUpdatedState(waitingMapTap)
    val correctionModeState by androidx.compose.runtime.rememberUpdatedState(correctionMode)
    val wallTracingState by androidx.compose.runtime.rememberUpdatedState(wallTracing)
    val onMarkerTappedForCorrectionState by androidx.compose.runtime.rememberUpdatedState(onMarkerTappedForCorrection)
    val onMapTappedState by androidx.compose.runtime.rememberUpdatedState(onMapTapped)

    // Última ubicación conocida del usuario → punto azul en el mapa.
    val userLoc = rememberUserLocation()

    // Bloque seleccionado (para popup) y bloque al que añadir vías
    var selectedBlock by remember { mutableStateOf<Block?>(null) }
    // Mini-ficha flotante de PARKING/ZONA: se pinta sobre el mapa sin taparlo
    // (la ficha grande queda solo para PIEDRAS, que sí tienen contenido).
    var miniBlock by remember { mutableStateOf<Block?>(null) }
    var editingMiniBlock by remember { mutableStateOf<Block?>(null) }
    var confirmDeleteMini by remember { mutableStateOf<Block?>(null) }
    // Vía objetivo del deep-link del diario → el detalle abre por su foto/cara.
    var highlightVia by remember { mutableStateOf<String?>(null) }
    var addingLinesTo by remember { mutableStateOf<Block?>(null) }
    // Estado ELEVADO del editor de edición (AddLinesFlow controlado): sobrevive
    // mientras el diálogo se oculta para trazar el muro en el mapa.
    var editFaces by remember { mutableStateOf<List<com.meteomontana.android.ui.screens.detail.EditFace>>(emptyList()) }
    var editGeometry by remember { mutableStateOf("POINT") }
    var editDirection by remember { mutableStateOf("LTR") }
    var editSelectedFace by remember { mutableStateOf(0) }
    var editTracedPath by remember { mutableStateOf<List<Pair<Double, Double>>?>(null) }
    // Trazado del muro DESDE el editor: modo activo + polilínea en construcción.
    var editWallTracing by remember { mutableStateOf(false) }
    var editWallPreview by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    val editWallTracingState by androidx.compose.runtime.rememberUpdatedState(editWallTracing)
    val onEditWallTapState by androidx.compose.runtime.rememberUpdatedState<(Double, Double) -> Unit> { lat, lon ->
        editWallPreview = editWallPreview + (lat to lon)
    }
    // El estado del editor se inicializa al pulsar "editar" (en onAddLines), no
    // aquí, para que el editor abra ya poblado y no haya un frame vacío.

    // ¿El usuario actual es admin? → puede borrar piedras/zonas/parkings.
    val isAdminUser = (viewModel.uiState.collectAsState().value
        as? com.meteomontana.android.ui.screens.detail.SchoolDetailUiState.Success)?.isCurrentUserAdmin == true

    // Deep-link del diario: abre la piedra que contiene la vía objetivo. Preferimos
    // el id ESTABLE de la vía (aguanta renombres/reordenes/muros); si no, por nombre.
    val autoOpenVia by viewModel.autoOpenVia.collectAsState()
    val autoOpenViaId by viewModel.autoOpenViaId.collectAsState()
    androidx.compose.runtime.LaunchedEffect(blocks, autoOpenVia, autoOpenViaId) {
        val viaId = autoOpenViaId?.takeIf { it.isNotBlank() }
        val via = autoOpenVia?.takeIf { it.isNotBlank() }
        if (viaId == null && via == null) return@LaunchedEffect
        if (blocks.isEmpty()) return@LaunchedEffect
        // 1) Por id estable.
        val byId = viaId?.let { id ->
            blocks.firstOrNull { b -> b.lines.any { it.id == id } }
        }
        val target = byId
            ?: via?.let { v -> blocks.firstOrNull { b -> b.lines.any { it.name.equals(v, ignoreCase = true) } } }
            ?: via?.let { v -> blocks.firstOrNull { it.name.equals(v, ignoreCase = true) } }
        if (target != null) {
            selectedBlock = target
            // El detalle abre por la foto que contiene esta vía (highlight por nombre).
            highlightVia = byId?.lines?.firstOrNull { it.id == viaId }?.name ?: via
            viewModel.consumeAutoOpenVia()
        }
    }
    val mapScope = androidx.compose.runtime.rememberCoroutineScope()
    var editingLine by remember {
        mutableStateOf<Pair<Block, com.meteomontana.android.domain.model.BlockLine>?>(null)
    }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Marker virtual de la escuela (para tap → proponer mover escuela entera).
    val schoolMarker = remember(schoolName, centerLat, centerLon) {
        Block(
            id = "__SCHOOL__", schoolId = schoolId, type = "SCHOOL",
            name = schoolName.ifBlank { "ESCUELA" },
            lat = centerLat, lon = centerLon,
            photoPath = null, description = null,
            createdByUid = "", createdAt = "", lines = emptyList()
        )
    }
    // Sectores colapsados: sus piedras se ocultan (tocar la zona alterna).
    var collapsedSectors by remember { mutableStateOf(setOf<String>()) }
    val collapsedState by androidx.compose.runtime.rememberUpdatedState(collapsedSectors)

    val visibleMarkers = remember(blocks, schoolMarker, collapsedSectors) {
        listOf(schoolMarker) + blocks.filter { b ->
            !(b.type == "BLOCK" && b.sectorBlockId != null && b.sectorBlockId in collapsedSectors)
        }
    }

    // Vuela a la "zona" de un parking: encuadra el parking + sectores/piedras a
    // ≤800 m (expandiendo colapsados) — vista de ~1,5-2 km. Sin nada cerca,
    // zoom equivalente.
    val flyToParkingZone: (Block) -> Unit = { parking ->
        val near = blocks.filter { b ->
            b.id != parking.id &&
                Geo.haversineKm(parking.lat, parking.lon, b.lat, b.lon) <= 0.8
        }
        val nearZoneIds = near.filter { it.type == "ZONE" }.map { it.id } +
            near.mapNotNull { it.sectorBlockId }
        if (nearZoneIds.isNotEmpty()) collapsedSectors = collapsedSectors - nearZoneIds.toSet()
        mapRef.value?.let { map ->
            runCatching {
                if (near.isEmpty()) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(parking.lat, parking.lon), 14.3))
                } else {
                    val bb = LatLngBounds.Builder().include(LatLng(parking.lat, parking.lon))
                    near.forEach { bb.include(LatLng(it.lat, it.lon)) }
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bb.build(), 130))
                }
            }
        }
    }

    // Tap de marcador: ZONA con piedras → colapsa/expande sus piedras; resto → popup.
    val onBlockTap: (Block) -> Unit = { tapped ->
        if (tapped.id != "__SCHOOL__") {
            when (tapped.type) {
                // Parking: mini-ficha + volar a su zona (el mapa se sigue viendo).
                "PARKING" -> { miniBlock = tapped; flyToParkingZone(tapped) }
                // Zona: mini-ficha + encuadrar el sector con sus piedras
                // (expandiéndolas — "me lleva a la zona", igual que el parking).
                "ZONE" -> {
                    miniBlock = tapped
                    val stones = blocks.filter { it.sectorBlockId == tapped.id }
                    if (stones.isNotEmpty()) collapsedSectors = collapsedSectors - tapped.id
                    mapRef.value?.let { map ->
                        runCatching {
                            if (stones.isEmpty()) {
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                    LatLng(tapped.lat, tapped.lon), 16.0))
                            } else {
                                val bb = LatLngBounds.Builder()
                                    .include(LatLng(tapped.lat, tapped.lon))
                                stones.forEach { bb.include(LatLng(it.lat, it.lon)) }
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngBounds(bb.build(), 130))
                            }
                        }
                    }
                }
                // Piedra: centra suave en ella y abre su ficha completa.
                else -> {
                    mapRef.value?.let { map ->
                        runCatching {
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(tapped.lat, tapped.lon), 16.5))
                        }
                    }
                    selectedBlock = tapped
                }
            }
        }
    }

    // Preview de muro activo: el del editor si está trazando, si no el del flujo crear.
    val activePreview = if (editWallTracing) editWallPreview else wallPreview

    // Re-pinta markers cuando cambia el ghost, el preview del muro o se colapsa un sector.
    androidx.compose.runtime.LaunchedEffect(correctionGhost, visibleMarkers, activePreview) {
        val map = mapRef.value ?: return@LaunchedEffect
        placeMarkers(ctx, map, visibleMarkers, correctionGhost, userLoc, activePreview) { tapped ->
            if (correctionModeState) onMarkerTappedForCorrectionState(tapped)
            else onBlockTap(tapped)
        }
    }

    MapViewLifecycleEffect(mapViewRef) { mapRef.value = null }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Selector de estilo
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapStyleOption.entries.forEach { option ->
                StyleChip(stringResource(option.labelResId), currentStyle == option) {
                    if (currentStyle != option) {
                        currentStyle = option
                        mapViewRef.value?.getMapAsync { map ->
                            map.setStyle(Style.Builder().fromJson(styleJsonFor(option))) {
                                placeMarkers(ctx, map, visibleMarkers, correctionGhost, userLoc, activePreview) { tapped ->
                                    if (correctionModeState) onMarkerTappedForCorrectionState(tapped)
                                    else onBlockTap(tapped)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Banner contextual con estado preciso del flujo.
        if (waitingMapTap || correctionMode) {
            val bannerText = when {
                !correctionMode -> "ℹ PULSA EN EL MAPA EN LA POSICIÓN ELEGIDA"
                correctionTargetName == null ->
                    "ℹ PULSA EL MARKER (PIEDRA / PARKING / ZONA / ESCUELA) QUE QUIERES MOVER"
                correctionGhost?.newLat == null ->
                    "✓ HAS PULSADO \"${correctionTargetName}\" · AHORA PULSA LA NUEVA POSICIÓN EN EL MAPA"
                else ->
                    "✓ POSICIÓN FIJADA PARA \"${correctionTargetName}\" · PULSA OTRA VEZ PARA RECORREGIR O ACEPTAR"
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(Terra)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bannerText, style = EyebrowTextStyle, color = Color.White,
                        modifier = Modifier.weight(1f))
                    Text(" ✕", color = Color.White, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = onCancelTap))
                }
                // Botón ACEPTAR cuando hay posición candidata fijada.
                if (correctionGhost?.newLat != null && correctionGhost.newLon != null) {
                    Spacer(Modifier.size(Spacing.sm))
                    Box(modifier = Modifier.fillMaxWidth()
                        .background(Color.White)
                        .clickable(onClick = onAcceptCorrection)
                        .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓ ACEPTAR", style = EyebrowTextStyle, color = Terra)
                    }
                }
            }
        }

        // Banner del trazado de muro: cada tap añade un punto; DESHACER / LISTO.
        // Sirve para el flujo CREAR (wallTracing) y el de EDITAR (editWallTracing).
        if (wallTracing || editWallTracing) {
            val pv = if (editWallTracing) editWallPreview else wallPreview
            val onUndo: () -> Unit = if (editWallTracing) ({ editWallPreview = editWallPreview.dropLast(1) }) else onWallUndo
            val onDone: () -> Unit = if (editWallTracing) ({ editTracedPath = editWallPreview; editWallTracing = false }) else onWallDone
            val onCancel: () -> Unit = if (editWallTracing) ({ editWallTracing = false }) else onCancelTap
            Column(
                modifier = Modifier.fillMaxWidth().background(Terra)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✎ TRAZA EL MURO · ${pv.size} PUNTOS · TOCA LA BASE DEL MURO",
                        style = EyebrowTextStyle, color = Color.White,
                        modifier = Modifier.weight(1f))
                    Text(" ✕", color = Color.White, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = onCancel))
                }
                Spacer(Modifier.size(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(modifier = Modifier.weight(1f)
                        .background(Color.White.copy(alpha = if (pv.isEmpty()) 0.4f else 1f))
                        .clickable(enabled = pv.isNotEmpty(), onClick = onUndo)
                        .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↶ DESHACER", style = EyebrowTextStyle, color = Terra)
                    }
                    Box(modifier = Modifier.weight(1f)
                        .background(Color.White.copy(alpha = if (pv.size >= 2) 1f else 0.4f))
                        .clickable(enabled = pv.size >= 2, onClick = onDone)
                        .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓ LISTO", style = EyebrowTextStyle, color = Terra)
                    }
                }
            }
        }

        // MapView con botón "+ PROPONER" superpuesto
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                factory = { context ->
                    MapView(context).apply {
                        onCreate(null)
                        mapViewRef.value = this
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                android.view.MotionEvent.ACTION_DOWN ->
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL ->
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        getMapAsync { map ->
                            mapRef.value = map
                            map.setStyle(Style.Builder().fromJson(styleJsonFor(currentStyle))) {
                                // Encuadre provisional mientras se calcula el fitBounds
                                // (evita un frame en (0,0)); el fitBounds de abajo lo sustituye.
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(centerLat, centerLon))
                                    .zoom(15.0).build()
                                placeMarkers(ctx, map, visibleMarkers, correctionGhost, userLoc, activePreview) { tapped ->
                                    if (correctionModeState) onMarkerTappedForCorrectionState(tapped)
                                    else onBlockTap(tapped)
                                }
                                // Encuadre inicial con TODOS los elementos (solo al abrir el
                                // mapa por primera vez; los re-pintados posteriores —al
                                // colapsar un sector, cambiar de estilo…— no vuelven a mover
                                // la cámara).
                                fitSchoolBoundsCameraUpdate(visibleMarkers, userLoc)?.let { update ->
                                    runCatching { map.moveCamera(update) }
                                }
                            }
                            map.uiSettings.apply {
                                isRotateGesturesEnabled = true
                                isTiltGesturesEnabled   = false
                            }
                            map.addOnMapClickListener { point ->
                                when {
                                    editWallTracingState -> {
                                        onEditWallTapState(point.latitude, point.longitude)
                                        true
                                    }
                                    waitingMapTapState || correctionModeState || wallTracingState -> {
                                        onMapTappedState(point.latitude, point.longitude)
                                        true
                                    }
                                    else -> {
                                        // Tap fuera de marker → cierra popup
                                        selectedBlock = null
                                        false
                                    }
                                }
                            }
                        }
                        onStart(); onResume()
                    }
                },
                update = { _ ->
                    // Sin re-registrar listener — el del factory lee siempre
                    // los flags actuales waitingMapTap/correctionMode vía closure.
                }
            )

            // Botón "+ PROPONER" (esquina inferior derecha)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.sm)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Terra)
                    .clickable(onClick = onProposeClick)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text(stringResource(R.string.detail_propose), style = EyebrowTextStyle, color = Color.White)
            }

            // Mini-ficha flotante de parking/sector: informa y da CÓMO LLEGAR
            // sin tapar el mapa (la ficha grande queda para las piedras).
            miniBlock?.let { mb ->
                MiniBlockCard(
                    block = mb,
                    stoneCount = blocks.count { it.sectorBlockId == mb.id },
                    collapsed = mb.id in collapsedSectors,
                    userLoc = userLoc,
                    isAdmin = isAdminUser,
                    onToggleStones = {
                        collapsedSectors =
                            if (mb.id in collapsedSectors) collapsedSectors - mb.id
                            else collapsedSectors + mb.id
                    },
                    onEdit = { editingMiniBlock = mb },
                    onDelete = { confirmDeleteMini = mb },
                    onClose = { miniBlock = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
                )
            }
        }

        // ── Lista de parkings ────────────────────────────────────────────
        // Lo importante para llegar: desde qué parking se va andando. Orden
        // alfabético (NO por cercanía — la lista debe tener sentido también
        // viéndola desde casa, lejos de la escuela). La distancia se muestra
        // solo como dato informativo. Pulsar uno lleva el mapa a SU zona
        // (encuadra parking + sectores/piedras cercanos, expandiéndolos). La
        // ficha con "CÓMO LLEGAR" se abre tocando el marker en el mapa.
        val parkings = remember(blocks) { blocks.filter { it.type == "PARKING" }.sortedBy { it.name } }
        if (parkings.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Text(
                    "PARKINGS",
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
                parkings.forEach { parking ->
                    val distanceText = userLoc?.let { loc ->
                        val km = Geo.haversineKm(loc.lat, loc.lon, parking.lat, parking.lon)
                        if (km < 1.0) "${(km * 1000).toInt()} m" else "${"%.1f".format(km)} km"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Puerta de entrada: mini-ficha + volar a su zona.
                                miniBlock = parking
                                flyToParkingZone(parking)
                            }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1A56DB)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("P", color = Color.White, style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold)
                            }
                            Text(
                                parking.name.ifBlank { "Parking" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        if (distanceText != null) {
                            Text(distanceText, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Dialog de detalles (foto + líneas + vías + CÓMO LLEGAR).
    // No incluye BORRAR — esa acción vive en el panel admin (FullScreenMapDialog).
    // ── Admin desde la mini-ficha: editar / eliminar ──
    editingMiniBlock?.let { mb ->
        EditBlockDialog(
            block = mb,
            onSave = { req ->
                mapScope.launch {
                    viewModel.adminUpdateBlock(mb.id, req)
                    editingMiniBlock = null
                    miniBlock = null
                }
            },
            onDismiss = { editingMiniBlock = null }
        )
    }
    confirmDeleteMini?.let { mb ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeleteMini = null },
            title = { Text("¿Eliminar «${mb.name.ifBlank { if (mb.type == "PARKING") "parking" else "sector" }}»?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.deleteBlock(mb.id) {}
                    confirmDeleteMini = null
                    miniBlock = null
                }) { Text("ELIMINAR", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDeleteMini = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    selectedBlock?.let { block ->
        val sectors = blocks.filter { it.type == "ZONE" }
        // Vías ya hechas (diario + cola offline pendiente) → marcadas ✓ al abrir.
        val doneKeys by viewModel.doneViaKeys.collectAsState()
        val doneLineIds = remember(block, doneKeys) {
            block.lines.mapIndexedNotNull { idx, line ->
                val viaName = line.name.ifBlank { "Vía ${idx + 1}" }
                val key = "${block.schoolId}|${viaName.trim().lowercase()}"
                if (doneKeys.contains(key)) line.id else null
            }.toSet()
        }
        // Vías marcadas como PROYECTO → mismo mecanismo que doneLineIds.
        val projectKeys by viewModel.projectViaKeys.collectAsState()
        val projectLineIds = remember(block, projectKeys) {
            block.lines.mapIndexedNotNull { idx, line ->
                val viaName = line.name.ifBlank { "Vía ${idx + 1}" }
                val key = "${block.schoolId}|${viaName.trim().lowercase()}"
                if (projectKeys.contains(key)) line.id else null
            }.toSet()
        }
        BlockDetailDialog(
            block = block,
            highlightVia = highlightVia,
            initiallyTicked = doneLineIds,
            initiallyProjects = projectLineIds,
            onAddLines = if (block.type == "BLOCK") ({
                // Inicializa el estado del editor AQUÍ (no en un LaunchedEffect)
                // para que abra ya poblado y no haya un frame vacío (el "salto").
                editFaces = com.meteomontana.android.ui.screens.detail.initialEditFaces(block)
                editGeometry = block.geometry.ifBlank { "POINT" }
                editDirection = block.direction.ifBlank { "LTR" }
                editSelectedFace = 0
                editTracedPath = null
                editWallTracing = false
                editWallPreview = emptyList()
                addingLinesTo = block
                // NO cerramos la ficha: el editor abre ENCIMA (su scrim tapa la
                // ficha) → sin parpadeo del mapa entre diálogos. Solo al trazar el
                // muro se oculta la ficha para ver el mapa (ver onTraceWall).
            }) else null,
            onEditLine = if (block.type == "BLOCK") ({ line ->
                editingLine = block to line
                // Igual que arriba: el editor abre encima sin cerrar la ficha.
            }) else null,
            onRateLine = if (block.type == "BLOCK") ({ lineId, stars ->
                viewModel.viewModelScope.launch {
                    if (stars > 0) viewModel.rateLine(block.id, lineId, stars)
                    else viewModel.unrateLine(block.id, lineId)
                }
            }) else null,
            onTickLine = if (block.type == "BLOCK") ({ line, idx ->
                val sectorName = sectors.firstOrNull { it.id == block.sectorBlockId }?.name
                // Toggle: marca/desmarca en el diario. SIN diálogo de "propuesta"
                // (el ✓ de la fila es el feedback); así no parece una propuesta ni
                // navega a "solicitudes".
                viewModel.viewModelScope.launch {
                    viewModel.toggleLine(block, line, idx, schoolName, sectorName)
                }
            }) else null,
            onToggleProject = if (block.type == "BLOCK") ({ line, idx ->
                val sectorName = sectors.firstOrNull { it.id == block.sectorBlockId }?.name
                viewModel.viewModelScope.launch {
                    viewModel.toggleProject(block, line, idx, schoolName, sectorName)
                }
            }) else null,
            availableSectors = sectors.takeIf { it.isNotEmpty() },
            onAssignSector = if (block.type == "BLOCK" && sectors.isNotEmpty()) ({ sectorId ->
                selectedBlock = null
                // Usamos viewModelScope para que la llamada no se cancele cuando
                // la composición del dialog desaparece.
                viewModel.viewModelScope.launch {
                    val r = viewModel.submitAssignSectorContribution(
                        targetBlockId = block.id,
                        targetLat = block.lat,
                        targetLon = block.lon,
                        sectorBlockId = sectorId
                    )
                    successMessage = if (r.isSuccess)
                        if (isAdminUser) "Publicado en el mapa." else "Propuesta enviada. Un admin la revisará en 24-48h."
                    else
                        "No se pudo enviar la propuesta: ${r.exceptionOrNull()?.message ?: "error"}"
                }
            }) else null,
            // Admin: borrar la piedra/zona/parking directamente desde el mapa.
            onDelete = if (isAdminUser) ({
                val id = block.id
                selectedBlock = null
                viewModel.deleteBlock(id) {}
            }) else null,
            onDismiss = { selectedBlock = null; highlightVia = null }
        )
    }

    // Flujo "+ AÑADIR VÍAS" / editar piedra-muro. Se oculta mientras se traza el
    // muro en el mapa (el estado vive arriba, así no se pierde lo editado).
    addingLinesTo?.let { block ->
        if (!editWallTracing) {
            AddLinesFlow(
                block = block,
                viewModel = viewModel,
                faces = editFaces,
                onFacesChange = { editFaces = it },
                selectedFace = editSelectedFace,
                onSelectedFaceChange = { editSelectedFace = it },
                geometry = editGeometry,
                onGeometryChange = { editGeometry = it },
                direction = editDirection,
                onDirectionChange = { editDirection = it },
                tracedPath = editTracedPath,
                onTraceWall = {
                    editWallPreview = emptyList(); editWallTracing = true
                    selectedBlock = null  // deja ver el mapa para trazar
                },
                onDismiss = { addingLinesTo = null; selectedBlock = null },
                onSuccess = {
                    addingLinesTo = null
                    selectedBlock = null
                    successMessage = if (isAdminUser) "Publicado en el mapa." else "Propuesta enviada. Un admin la revisará en 24-48h."
                }
            )
        }
    }

    // Flujo "✎ CORREGIR VÍA" — redibuja una línea concreta
    editingLine?.let { (block, line) ->
        com.meteomontana.android.ui.screens.detail.EditLineFlow(
            block = block,
            line = line,
            viewModel = viewModel,
            onDismiss = { editingLine = null; selectedBlock = null },
            onSuccess = {
                editingLine = null
                selectedBlock = null
                successMessage = if (isAdminUser) "Publicado en el mapa." else "Propuesta enviada. Un admin la revisará en 24-48h."
            }
        )
    }

    // Aviso de éxito tras enviar la propuesta — mismo estilo que el flujo normal
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

/**
 * Diálogo de éxito estilo Cumbre (mismo look que ProposeContributionFlow.SuccessDialog).
 * Se usa tras enviar la propuesta de "AÑADIR VÍAS".
 */
@Composable
private fun CumbreSuccessDialog(
    onClose: () -> Unit,
    onMyProposals: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(Spacing.lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(com.meteomontana.android.ui.theme.Moss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", style = MaterialTheme.typography.headlineLarge,
                        color = Color.White)
                }
                Spacer(Modifier.height(Spacing.lg))
                Text("PROPUESTA ENVIADA",
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text("Un admin la revisará en ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("24-48h.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Terra)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Te avisaremos por email y notificación\npush cuando haya respuesta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xl))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(modifier = Modifier.weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(onClick = onClose)
                        .padding(vertical = Spacing.md),
                        contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.common_close).uppercase(), style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    Box(modifier = Modifier.weight(1.5f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.onBackground)
                        .clickable(onClick = onMyProposals)
                        .padding(vertical = Spacing.md),
                        contentAlignment = Alignment.Center) {
                        Text("VER MIS PROPUESTAS", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }
}

// Mapa de markers activos para poder mapear marker → Block al tocar
private val markerBlockMap = mutableMapOf<Long, Block>()
private val polylineBlockMap = mutableMapOf<Long, Block>()

/** Parsea el `path` de un muro ("[[lat,lon],...]") a LatLng. Vacío si no es válido. */
internal fun parseWallPath(path: String?): List<LatLng> {
    if (path.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(path)
        (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONArray(i) ?: return@mapNotNull null
            if (p.length() < 2) null else LatLng(p.getDouble(0), p.getDouble(1))
        }
    } catch (e: Exception) { emptyList() }
}

/** Color terra de las piedras: el muro usa el MISMO color (solo que como línea). */
private const val PIEDRA_COLOR = "#C2410C"

private fun placeMarkers(
    ctx: android.content.Context,
    map: MapLibreMap,
    blocks: List<Block>,
    ghost: com.meteomontana.android.ui.screens.detail.CorrectionGhost? = null,
    userLoc: com.meteomontana.android.domain.model.UserLocation? = null,
    wallPreview: List<Pair<Double, Double>> = emptyList(),
    onBlockTap: (Block) -> Unit
) {
    map.clear()
    markerBlockMap.clear()
    polylineBlockMap.clear()

    val iconFactory = IconFactory.getInstance(ctx)

    // Punto azul con la posición del usuario (si la tenemos): así se ve
    // cómo de cerca estás de la escuela y sus sectores.
    if (userLoc != null) {
        map.addMarker(
            MarkerOptions()
                .position(LatLng(userLoc.lat, userLoc.lon))
                .icon(iconFactory.fromBitmap(userDotBitmap()))
        )
    }

    blocks.forEach { b ->
        // Si este marker está siendo movido (es el original), lo pintamos semitransparente.
        val isOriginalBeingMoved = ghost != null && (
            (ghost.originalId == null && b.id == "__SCHOOL__") ||
            (ghost.originalId != null && b.id == ghost.originalId)
        )
        // MURO (geometry=LINE) con polilínea válida: se pinta como LÍNEA del MISMO
        // color que la piedra (no como marcador) → 1 muro = 1 línea, no 16 pines.
        // El número va en un marcador en el punto medio (tappable, mismo estilo).
        val wallPath = if (b.type.equals("BLOCK", true) && b.geometry.equals("LINE", true))
            parseWallPath(b.path) else emptyList()
        if (wallPath.size >= 2) {
            val polyline = map.addPolyline(
                PolylineOptions()
                    .addAll(wallPath)
                    .color(android.graphics.Color.parseColor(PIEDRA_COLOR))
                    .alpha(if (isOriginalBeingMoved) 0.35f else 1f)
                    .width(5f)
            )
            polylineBlockMap[polyline.id] = b
            val mid = wallPath[wallPath.size / 2]
            val midMarker = map.addMarker(
                MarkerOptions()
                    .position(mid)
                    .icon(iconFactory.fromBitmap(blockBitmap(b.name).fadedIf(isOriginalBeingMoved)))
                    .title(b.name)
            )
            markerBlockMap[midMarker.id] = b
            return@forEach
        }

        val icon = when (b.type.uppercase()) {
            "PARKING" -> iconFactory.fromBitmap(parkingBitmap().fadedIf(isOriginalBeingMoved))
            "ZONE"    -> iconFactory.fromBitmap(zoneBitmap().fadedIf(isOriginalBeingMoved))
            "SCHOOL"  -> iconFactory.fromBitmap(schoolBitmap(b.name).fadedIf(isOriginalBeingMoved))
            else      -> iconFactory.fromBitmap(blockBitmap(b.name).fadedIf(isOriginalBeingMoved))
        }
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(b.lat, b.lon))
                .icon(icon)
                .title(b.name)
        )
        markerBlockMap[marker.id] = b
    }

    // Preview del muro en construcción: polilínea terra + un punto numerado por tap.
    if (wallPreview.isNotEmpty()) {
        val pts = wallPreview.map { (lat, lon) -> LatLng(lat, lon) }
        if (pts.size >= 2) {
            map.addPolyline(
                PolylineOptions()
                    .addAll(pts)
                    .color(android.graphics.Color.parseColor(PIEDRA_COLOR))
                    .width(5f)
            )
        }
        pts.forEachIndexed { idx, p ->
            map.addMarker(
                MarkerOptions()
                    .position(p)
                    .icon(iconFactory.fromBitmap(wallPointBitmap(idx + 1)))
            )
        }
    }

    // Ghost marker en la nueva posición candidata.
    if (ghost?.newLat != null && ghost.newLon != null) {
        map.addMarker(
            MarkerOptions()
                .position(LatLng(ghost.newLat, ghost.newLon))
                .icon(iconFactory.fromBitmap(ghostBitmap()))
                .title("Nueva posición")
        )
    }

    map.setOnMarkerClickListener { marker ->
        markerBlockMap[marker.id]?.let { onBlockTap(it) }
        true
    }
    // Tocar la LÍNEA de un muro abre su ficha (igual que tocar su número).
    map.setOnPolylineClickListener { polyline ->
        polylineBlockMap[polyline.id]?.let { onBlockTap(it) }
    }
}

/** Aplica alpha 0.35f a un bitmap si [faded] es true. */
private fun Bitmap.fadedIf(faded: Boolean): Bitmap {
    if (!faded) return this
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 90 }
    c.drawBitmap(this, 0f, 0f, paint)
    return out
}

/** Marker fantasma terra con ★ para la posición candidata. */
private fun ghostBitmap(): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#C2410C")
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = android.graphics.Color.WHITE
    }
    c.drawCircle(size / 2f, size / 2f, 24f, fill)
    c.drawCircle(size / 2f, size / 2f, 24f, border)
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = 28f
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText("★", size / 2f, size / 2f + 10f, txt)
    return bmp
}

/** Punto numerado de la polilínea del muro en construcción (preview del trazado). */
private fun wallPointBitmap(number: Int): Bitmap {
    val size = 56
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor(PIEDRA_COLOR)
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = android.graphics.Color.WHITE
    }
    c.drawCircle(size / 2f, size / 2f, 20f, fill)
    c.drawCircle(size / 2f, size / 2f, 20f, border)
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = 24f
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText(number.toString(), size / 2f, size / 2f + 9f, txt)
    return bmp
}

/** Símbolo de escuela: triángulo terra estilo "montaña" para marcar el centro. */
private fun schoolBitmap(label: String): Bitmap {
    val size = 72
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1C1C1A")
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.WHITE
    }
    val path = android.graphics.Path().apply {
        // Triángulo apuntando arriba (montaña)
        moveTo(size / 2f, 8f)
        lineTo(size - 8f, size - 10f)
        lineTo(8f, size - 10f)
        close()
    }
    c.drawPath(path, fill)
    c.drawPath(path, border)
    // Punto blanco dentro para que destaque
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    c.drawCircle(size / 2f, size / 2f + 4f, 5f, dot)
    return bmp
}

/** Cuadrado azul con "P" blanca — símbolo internacional de parking. */
private fun parkingBitmap(): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)

    // Fondo azul redondeado
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#1a56db") }
    c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 10f, 10f, bg)

    // Borde blanco
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
        strokeWidth = 3f
    }
    c.drawRoundRect(RectF(1.5f, 1.5f, size - 1.5f, size - 1.5f), 9f, 9f, stroke)

    // "P" blanca
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText("P", size / 2f, size / 2f + 12f, txt)
    return bmp
}

/**
 * Pin de piedra (BLOCK). Polígono irregular terra con el nombre dentro.
 * Usa el mismo helper compartido que el mapa del admin para coherencia visual.
 */
private fun blockBitmap(label: String): Bitmap = pinBitmapBoulder(
    label = label.takeIf { it.isNotBlank() } ?: "?",
    fillColor = android.graphics.Color.parseColor("#C2410C"),
    sizeDp = 22   // ↓ Más pequeñas: en zonas con muchas piedras juntas, no se solapan tanto.
)

/** Pin verde para zonas (tipo ZONE). */
private fun zoneBitmap(): Bitmap {
    val size = 52
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#3F6B4A") }
    val cx = size / 2f; val cy = size / 2f - 4f; val r = 20f
    c.drawCircle(cx, cy, r, fill)
    val path = android.graphics.Path().apply {
        moveTo(cx - 8f, cy + r - 4f)
        lineTo(cx, cy + r + 10f)
        lineTo(cx + 8f, cy + r - 4f)
        close()
    }
    c.drawPath(path, fill)
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 20f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText("Z", cx, cy + 7f, txt)
    return bmp
}

@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape  = RoundedCornerShape(2.dp)
    val bg     = if (selected) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.surface
    val fg     = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val border = if (selected) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mini-ficha flotante de parking/sector sobre el mapa. Una sola línea de alto:
// icono + nombre + subtítulo, y a la derecha (admin: ✎ 🗑) + CÓMO LLEGAR + ✕.
// Para sectores con piedras añade el toggle VER/OCULTAR PIEDRAS explícito.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MiniBlockCard(
    block: Block,
    stoneCount: Int,
    collapsed: Boolean,
    userLoc: com.meteomontana.android.domain.model.UserLocation?,
    isAdmin: Boolean,
    onToggleStones: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val isParking = block.type == "PARKING"
    val badgeColor = if (isParking) Color(0xFF1A56DB) else Color(0xFF3F6B4A)
    val distance = userLoc?.let { loc ->
        val km = Geo.haversineKm(loc.lat, loc.lon, block.lat, block.lon)
        if (km < 1.0) "${(km * 1000).toInt()} m" else "${"%.1f".format(km)} km"
    }
    val subtitle = buildString {
        append(if (isParking) "Parking" else "Sector")
        if (!isParking && stoneCount > 0) append(" · $stoneCount piedra${if (stoneCount == 1) "" else "s"}")
        distance?.let { append(" · $it") }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier.size(24.dp)
                    .clip(if (isParking) RoundedCornerShape(6.dp) else androidx.compose.foundation.shape.CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isParking) "P" else "Z", color = Color.White,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(block.name.ifBlank { if (isParking) "Parking" else "Sector" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (isAdmin) {
                Text("✎", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit).padding(Spacing.xs))
                Text("🗑", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDelete).padding(Spacing.xs))
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Terra)
                    .clickable {
                        runCatching {
                            ctx.startActivity(android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "https://www.google.com/maps/dir/?api=1&destination=${block.lat},${block.lon}")
                            ))
                        }
                    }
                    .padding(horizontal = Spacing.sm, vertical = 7.dp)
            ) {
                Text("CÓMO LLEGAR", style = EyebrowTextStyle, color = Color.White)
            }
            Text("✕", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose).padding(Spacing.xs))
        }
        // Toggle explícito de piedras del sector (antes: tap "silencioso" en el marker).
        if (!isParking && stoneCount > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
                    .clickable(onClick = onToggleStones)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (collapsed) "VER PIEDRAS" else "OCULTAR PIEDRAS",
                    style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
