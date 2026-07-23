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
 * EL MAPA de la escuela (MapLibre): camara, estilos, capas, fullscreen,
 * botonera y mini-fichas. Espejo de MapLibreView.swift en iOS.
 *
 * No sabe nada del flujo de propuestas ni del editor: recibe el puente
 * (ProposalMapBridge) y el estado del editor (WallEditState) y solo los
 * consulta/invoca. La ficha de piedra vive en SchoolMap (izada).
 */

private enum class MapStyleOption(val labelResId: Int) {
    SATELLITE(R.string.map_satellite),
    TOPO(R.string.map_topo)
}

private fun styleJsonFor(style: MapStyleOption): String = when (style) {
    MapStyleOption.SATELLITE -> """{"version":8,"sources":{"sat":{"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"Tiles © Esri"}},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#F4F1E9"}},{"id":"sat","type":"raster","source":"sat"}]}"""
    MapStyleOption.TOPO      -> """{"version":8,"sources":{"topo":{"type":"raster","tiles":["https://a.tile.opentopomap.org/{z}/{x}/{y}.png","https://b.tile.opentopomap.org/{z}/{x}/{y}.png","https://c.tile.opentopomap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenTopoMap (CC-BY-SA)"}},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#F4F1E9"}},{"id":"topo","type":"raster","source":"topo"}]}"""
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

// ─── SchoolMapView (el mapa MapLibre en si) ─────────────────────────────────────────────────────────────────

@Composable
internal fun SchoolMapView(
    centerLat: Double,
    centerLon: Double,
    blocks: List<Block>,
    schoolName: String,
    schoolId: String,
    viewModel: SchoolDetailViewModel,
    /** Puente con el flujo "+ PROPONER": flags y callbacks SIEMPRE frescos
     *  (es @Stable con mutableStateOf → los listeners del factory leen por
     *  referencia; fuera los rememberUpdatedState que parcheaban esto). */
    bridge: ProposalMapBridge,
    /** Estado del editor de piedra/muro (izado en SchoolMap). */
    wallEdit: WallEditState,
    // Ficha de piedra: izada a SchoolMap (los deep-links abren la ficha sin
    // arrancar MapLibre). El mapa solo notifica taps.
    onBlockSelected: (Block) -> Unit,
    onDismissBlock: () -> Unit
) {
    val ctx = LocalContext.current
    var currentStyle by remember { mutableStateOf(MapStyleOption.SATELLITE) }
    val mapViewRef   = remember { mutableStateOf<MapView?>(null) }
    val mapRef       = remember { mutableStateOf<MapLibreMap?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Última ubicación conocida del usuario → punto azul en el mapa.
    val userLoc = rememberUserLocation()
    // Rumbo del móvil (brújula) → cono de dirección en el punto azul.
    val deviceHeading = rememberDeviceHeading()
    val headingState by androidx.compose.runtime.rememberUpdatedState(deviceHeading)

    // Mini-ficha flotante de PARKING/ZONA: se pinta sobre el mapa sin taparlo
    // (la ficha grande queda solo para PIEDRAS, que sí tienen contenido).
    var miniBlock by remember { mutableStateOf<Block?>(null) }
    var editingMiniBlock by remember { mutableStateOf<Block?>(null) }
    var confirmDeleteMini by remember { mutableStateOf<Block?>(null) }
    // onDismissBlock es lambda del padre → snapshot para que el listener del
    // factory (registrado una vez) lo lea fresco. bridge/wallEdit no lo
    // necesitan: son objetos @Stable leídos por referencia.
    val onDismissBlockState by androidx.compose.runtime.rememberUpdatedState(onDismissBlock)

    // ¿El usuario actual es admin? → puede borrar piedras/zonas/parkings.
    val isAdminUser = (viewModel.uiState.collectAsState().value
        as? com.meteomontana.android.ui.screens.detail.SchoolDetailUiState.Success)?.isCurrentUserAdmin == true

    val mapScope = androidx.compose.runtime.rememberCoroutineScope()
    // Sincronizador de anotaciones: estado de instancia (no global).
    val placer = remember { MarkerPlacer() }

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
    // Capas ocultas por el usuario (toggles de la leyenda): PARKING/BLOCK/ZONE.
    var hiddenTypes by remember { mutableStateOf(setOf<String>()) }
    // Mapa a pantalla completa (estilo Radar: el mapa es la pantalla).
    var fullscreenMap by remember { mutableStateOf(false) }

    val visibleMarkers = remember(blocks, schoolMarker, collapsedSectors, hiddenTypes) {
        listOf(schoolMarker) + blocks.filter { b ->
            b.type.uppercase() !in hiddenTypes &&
                !(b.type == "BLOCK" && b.sectorBlockId != null && b.sectorBlockId in collapsedSectors)
        }
    }

    // Vuela a la "zona" de un parking: encuadra el parking + sectores/piedras a
    // ≤800 m (expandiendo colapsados) — vista de ~1,5-2 km. Sin nada cerca,
    // zoom equivalente.
    val flyToParkingZone: (Block) -> Unit = { parking ->
        val near = com.meteomontana.android.domain.usecase.map.MapGeometry.blocksWithinKm(
            blocks, parking.lat, parking.lon, km = 0.8, excludeId = parking.id)
        val nearZoneIds = near.filter { it.type == "ZONE" }.map { it.id } +
            near.mapNotNull { it.sectorBlockId }
        if (nearZoneIds.isNotEmpty()) collapsedSectors = collapsedSectors - nearZoneIds.toSet()
        mapRef.value?.let { map ->
            runCatching {
                // Centrar SIEMPRE en el propio parking (antes usaba el centroide
                // parking+piedras y el parking quedaba descentrado). Zoom un poco
                // más cerca si hay elementos alrededor para dar contexto.
                val zoom = if (near.isEmpty()) 14.3 else 14.6
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(parking.lat, parking.lon), zoom))
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
                            // Zoom FIJO y predecible: los encuadres calculados
                            // se iban a los extremos según cómo estén los datos.
                            val pts = listOf(
                                com.meteomontana.android.domain.usecase.map.GeoPoint(tapped.lat, tapped.lon)
                            ) + stones.map { com.meteomontana.android.domain.usecase.map.GeoPoint(it.lat, it.lon) }
                            val c = com.meteomontana.android.domain.usecase.map.MapGeometry.centroid(pts)!!
                            // Nunca ALEJAR: si ya estás más cerca, solo centra.
                            val targetZoom = maxOf(map.cameraPosition.zoom, 15.0)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(c.lat, c.lon), targetZoom))
                        }
                    }
                }
                // Piedra: centra suave en ella y abre su ficha completa.
                else -> {
                    mapRef.value?.let { map ->
                        runCatching {
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(tapped.lat, tapped.lon), 15.2))
                        }
                    }
                    onBlockSelected(tapped)
                }
            }
        }
    }

    // Preview de muro activo: el del editor si está trazando, si no el del flujo crear.
    val activePreview = if (wallEdit.tracing) wallEdit.preview else bridge.wallPreview

    // Re-pinta markers cuando cambia el ghost, el preview del muro, se colapsa
    // un sector, te mueves o giras (brújula del punto azul).
    androidx.compose.runtime.LaunchedEffect(bridge.correctionGhost, visibleMarkers, activePreview, userLoc, deviceHeading) {
        val map = mapRef.value ?: return@LaunchedEffect
        placer.place(ctx, map, visibleMarkers, bridge.correctionGhost, userLoc, activePreview, deviceHeading) { tapped ->
            if (bridge.correctionMode) bridge.handleMarkerTapForCorrection(tapped)
            else onBlockTap(tapped)
        }
    }

    // Vigilante anti-desapariciones: si un re-pintado coincide con un gesto de
    // zoom, el SDK a veces pierde las anotaciones (piedras/sectores esfumados).
    // Al pararse la cámara, si el mapa está vacío cuando no debería, re-pintamos.
    val visibleState = androidx.compose.runtime.rememberUpdatedState(visibleMarkers)
    val ghostState = androidx.compose.runtime.rememberUpdatedState(bridge.correctionGhost)
    val previewState = androidx.compose.runtime.rememberUpdatedState(activePreview)
    androidx.compose.runtime.LaunchedEffect(mapRef.value) {
        val map = mapRef.value ?: return@LaunchedEffect
        var lastClusterZoom = map.cameraPosition.zoom
        map.addOnCameraIdleListener {
            val zoomChanged = kotlin.math.abs(map.cameraPosition.zoom - lastClusterZoom) > 0.4
            if (zoomChanged || (map.annotations.isEmpty() && visibleState.value.isNotEmpty())) {
                // Re-pinta al cambiar el zoom (los clústeres dependen de él) y
                // como vigilante anti-desapariciones del SDK.
                lastClusterZoom = map.cameraPosition.zoom
                placer.place(ctx, map, visibleState.value, ghostState.value,
                    userLoc, previewState.value) { tapped ->
                    if (bridge.correctionMode) bridge.handleMarkerTapForCorrection(tapped)
                    else onBlockTap(tapped)
                }
            }
        }
    }

    MapViewLifecycleEffect(mapViewRef) { mapRef.value = null }

    // Cámara guardada al entrar/salir de pantalla completa (el MapView se
    // recrea al cambiar de contenedor; sin esto perderías el encuadre).
    var savedCamera by remember { mutableStateOf<CameraPosition?>(null) }
    val toggleFullscreen: () -> Unit = {
        savedCamera = mapRef.value?.cameraPosition
        fullscreenMap = !fullscreenMap
    }
    val applyStyle: (MapStyleOption) -> Unit = { option ->
        if (currentStyle != option) {
            currentStyle = option
            mapViewRef.value?.getMapAsync { map ->
                map.setStyle(Style.Builder().fromJson(styleJsonFor(option))) {
                    placer.place(ctx, map, visibleMarkers, bridge.correctionGhost, userLoc, activePreview) { tapped ->
                        if (bridge.correctionMode) bridge.handleMarkerTapForCorrection(tapped)
                        else onBlockTap(tapped)
                    }
                }
            }
        }
    }
    // Toggle de capa (leyenda pulsable): oculta/enseña parkings, piedras o zonas.
    val toggleLayer: (String) -> Unit = { type ->
        hiddenTypes = if (type in hiddenTypes) hiddenTypes - type else hiddenTypes + type
    }

    // Banners del flujo de propuesta (compartidos entre modo normal y fullscreen).
    val flowBanners: @Composable () -> Unit = {
        // Banner contextual con estado preciso del flujo.
        if (bridge.waitingMapTap || bridge.correctionMode) {
            val bannerText = when {
                !bridge.correctionMode -> "ℹ PULSA EN EL MAPA EN LA POSICIÓN ELEGIDA"
                bridge.correctionTargetName == null ->
                    "ℹ PULSA EL MARKER (PIEDRA / PARKING / ZONA / ESCUELA) QUE QUIERES MOVER"
                bridge.correctionGhost?.newLat == null ->
                    "✓ HAS PULSADO \"${bridge.correctionTargetName}\" · AHORA PULSA LA NUEVA POSICIÓN EN EL MAPA"
                else ->
                    "✓ POSICIÓN FIJADA PARA \"${bridge.correctionTargetName}\" · PULSA OTRA VEZ PARA RECORREGIR O ACEPTAR"
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(Terra)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bannerText, style = EyebrowTextStyle, color = Color.White,
                        modifier = Modifier.weight(1f))
                    Text(" ✕", color = Color.White, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = bridge::reset))
                }
                // Botón ACEPTAR cuando hay posición candidata fijada.
                val ghost = bridge.correctionGhost
                if (ghost?.newLat != null && ghost.newLon != null) {
                    Spacer(Modifier.size(Spacing.sm))
                    Box(modifier = Modifier.fillMaxWidth()
                        .background(Color.White)
                        .clickable(onClick = bridge::acceptCorrection)
                        .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓ ACEPTAR", style = EyebrowTextStyle, color = Terra)
                    }
                }
            }
        }

        // Banner del trazado de muro: cada tap añade un punto; DESHACER / LISTO.
        // Sirve para el flujo CREAR (bridge.wallTracing) y el de EDITAR (wallEdit.tracing).
        if (bridge.wallTracing || wallEdit.tracing) {
            val pv = if (wallEdit.tracing) wallEdit.preview else bridge.wallPreview
            val onUndo: () -> Unit = if (wallEdit.tracing) wallEdit::undoPoint else bridge::wallUndo
            val onDone: () -> Unit = if (wallEdit.tracing) wallEdit::finishTracing else bridge::wallDone
            val onCancel: () -> Unit = if (wallEdit.tracing) wallEdit::cancelTracing else bridge::reset
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
    }

    // MapView con overlays (+ PROPONER, expandir/cerrar, mini-ficha, dónde
    // estoy). Compartido entre el modo tarjeta (280 dp) y pantalla completa.
    val mapBox: @Composable (Modifier) -> Unit = { boxModifier ->
        Box(modifier = boxModifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapView(context, org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
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
                                placer.place(ctx, map, visibleMarkers, bridge.correctionGhost, userLoc, activePreview) { tapped ->
                                    if (bridge.correctionMode) bridge.handleMarkerTapForCorrection(tapped)
                                    else onBlockTap(tapped)
                                }
                                // Encuadre inicial con TODOS los elementos, salvo que
                                // vengamos de entrar/salir de pantalla completa (ahí se
                                // restaura la cámara que tenía el usuario).
                                val restored = savedCamera
                                if (restored != null) {
                                    map.cameraPosition = restored
                                    savedCamera = null
                                } else {
                                    fitSchoolBoundsCameraUpdate(visibleMarkers, userLoc)?.let { update ->
                                        runCatching { map.moveCamera(update) }
                                    }
                                }
                            }
                            map.uiSettings.apply {
                                isRotateGesturesEnabled = true
                                isTiltGesturesEnabled   = false
                            }
                            map.addOnMapClickListener { point ->
                                when {
                                    wallEdit.tracing -> {
                                        wallEdit.addPoint(point.latitude, point.longitude)
                                        true
                                    }
                                    bridge.waitingMapTap || bridge.correctionMode || bridge.wallTracing -> {
                                        bridge.handleMapTap(point.latitude, point.longitude)
                                        true
                                    }
                                    else -> {
                                        // Tap fuera de marker → cierra popup
                                        onDismissBlockState()
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
                    // los flags actuales bridge.waitingMapTap/bridge.correctionMode vía closure.
                }
            )

            // Botón "+ PROPONER": pill flotante ARRIBA a la derecha —
            // abajo lo tapaba la mini-ficha de parking/sector.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Terra)
                    .clickable(onClick = { bridge.proposeOpen = true })
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text(stringResource(R.string.detail_propose), style = EyebrowTextStyle, color = Color.White)
            }

            // Ampliar / salir de pantalla completa — ARRIBA a la izquierda.
            Box(
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(Spacing.sm)
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline,
                        androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = toggleFullscreen),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    if (fullscreenMap) androidx.compose.material.icons.Icons.Outlined.CloseFullscreen
                    else androidx.compose.material.icons.Icons.Outlined.OpenInFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp))
            }

            // Botonera lateral (SIEMPRE, estilo Radar — maqueta B aprobada):
            // topo↔satélite de un toque + capas con la FORMA real del marcador
            // (P cuadrada azul, piedra polígono terra, Z círculo verde);
            // apagado = capa oculta.
            if (!bridge.waitingMapTap && !bridge.correctionMode && !bridge.wallTracing) {
                Column(
                    // ARRIBA a la derecha, bajo PROPONER — centrada se solapaba
                    // con el botón de ubicación (abajo a la derecha).
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = Spacing.sm, bottom = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Re-centrar en la escuela (vuelta al encuadre inicial).
                    SideMapButton(active = true, onClick = {
                        mapRef.value?.let { map ->
                            fitSchoolBoundsCameraUpdate(visibleMarkers, null)?.let { update ->
                                runCatching { map.animateCamera(update) }
                            }
                        }
                    }) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Outlined.GpsFixed,
                            contentDescription = "Centrar en la escuela",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp))
                    }
                    SideMapButton(active = true, onClick = {
                        applyStyle(if (currentStyle == MapStyleOption.SATELLITE)
                            MapStyleOption.TOPO else MapStyleOption.SATELLITE)
                    }) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Outlined.Layers,
                            contentDescription = "Topográfico/Satélite",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp))
                    }
                    SideMapButton(active = "PARKING" !in hiddenTypes,
                        onClick = { toggleLayer("PARKING") }) { ParkingShapeIcon() }
                    SideMapButton(active = "BLOCK" !in hiddenTypes,
                        onClick = { toggleLayer("BLOCK") }) { StoneShapeIcon() }
                    SideMapButton(active = "ZONE" !in hiddenTypes,
                        onClick = { toggleLayer("ZONE") }) { ZoneShapeIcon() }
                    if (userLoc != null) {
                        SideMapButton(active = true, onClick = {
                            mapRef.value?.let { map ->
                                runCatching {
                                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                        LatLng(userLoc.lat, userLoc.lon), 15.5))
                                }
                            }
                        }) {
                            Text("◎", color = Color(0xFF1A56DB),
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
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
    }

    // ── Pantalla completa: el mapa ES la pantalla (estilo Radar) ─────────
    if (fullscreenMap) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = toggleFullscreen,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                mapBox(Modifier.fillMaxSize())
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(bottom = Spacing.xl)
                ) { flowBanners() }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        flowBanners()

        if (!fullscreenMap) {
            mapBox(Modifier.fillMaxWidth().height(280.dp))
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
                // Chips horizontales (una línea): cada uno lleva el mapa a SU zona
                // (mini-ficha + encuadre parking + sectores/piedras cercanos).
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md)
                ) {
                    items(parkings.size) { i ->
                        val parking = parkings[i]
                        val distanceText = userLoc?.let { loc ->
                            val km = Geo.haversineKm(loc.lat, loc.lon, parking.lat, parking.lon)
                            if (km < 1.0) "${(km * 1000).toInt()} m" else "${"%.1f".format(km)} km"
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                .clickable {
                                    miniBlock = parking
                                    flyToParkingZone(parking)
                                }
                                .padding(horizontal = Spacing.sm, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF1A56DB)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("P", color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold)
                            }
                            Text(
                                parking.name.ifBlank { "Parking" } +
                                    (distanceText?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
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

}
