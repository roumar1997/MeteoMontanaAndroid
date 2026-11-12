package com.meteomontana.android.ui.components

import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.theme.terraFillColor

import com.meteomontana.android.data.map.MapStyles
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import com.meteomontana.android.ui.guide.guideTarget
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

@Composable
internal fun SchoolMapView(
    centerLat: Double,
    centerLon: Double,
    blocks: List<Block>,
    schoolName: String,
    schoolId: String,
    /** Abrir ya a pantalla completa (llegando desde "Aportar" con una foto). */
    startFullscreen: Boolean = false,
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
    onDismissBlock: () -> Unit,
    /** Piedras sin ninguna vía en la selección del filtro de grado (ver
     * docs/BLOCK_SEARCH_DESIGN.md §7) — se pintan atenuadas en el mapa. */
    gradeDimmedBlockIds: Set<String> = emptySet(),
    /** Camino grabado hasta un parking o sector (el verificado si hay), para «Seguir camino» en su tarjeta. */
    approachTo: (blockId: String) -> com.meteomontana.android.domain.model.Approach? = { null },
    onFollowApproach: (com.meteomontana.android.domain.model.Approach) -> Unit = {},
    /** Lo que va JUSTO debajo del mapa y encima de PARKINGS (aproximaciones).
     *  Es una ranura porque esos datos viven en la pantalla de detalle, pero su
     *  sitio en pantalla está aquí dentro — igual que en iOS. */
    contenidoTrasMapa: @Composable () -> Unit = {},
    /** Filtros (grado, vía/bloque) entre las filas de sectores/parkings y el mapa, como en iOS. */
    filtersContent: @Composable () -> Unit = {},
    /** Menú de orientación: sobre el mapa, a la izquierda de PROPONER. */
    orientationControl: @Composable () -> Unit = {}
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
    // onDismissBlock es lambda del padre → snapshot para que el listener del
    // factory (registrado una vez) lo lea fresco. bridge/wallEdit no lo
    // necesitan: son objetos @Stable leídos por referencia.
    val onDismissBlockState by androidx.compose.runtime.rememberUpdatedState(onDismissBlock)

    // Sincronizador de anotaciones: estado de instancia (no global).
    val placer = remember { MarkerPlacer() }

    // Marker virtual de la escuela (para tap → proponer mover escuela entera).
    val schoolMarker = remember(schoolName, centerLat, centerLon) {
        Block(
            id = "__SCHOOL__", schoolId = schoolId, type = "SCHOOL",
            name = schoolName.ifBlank { AppText.get(R.string.w_school_caps) },
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
    // Con una foto por confirmar, el mapa arranca a PANTALLA COMPLETA: la
    // pregunta "¿es el sitio?" solo se puede responder viendo el mapa con
    // holgura, y en la tarjeta de 280 dp no se distingue nada.
    var fullscreenMap by remember { mutableStateOf(startFullscreen) }

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
                            val targetZoom = maxOf(map.cameraPosition.zoom, ZOOM_SECTOR)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(c.lat, c.lon), targetZoom))
                        }
                    }
                }
                // Piedra: centra suave en ella y abre su ficha completa.
                else -> {
                    mapRef.value?.let { map ->
                        runCatching {
                            // NUNCA alejar, igual que al pulsar un sector. Antes
                            // forzaba 15.2 EXACTO: si te habías acercado con los
                            // dedos para distinguir las piedras, tocar una te
                            // devolvía atrás, y al cerrar su ficha tocaba rehacer
                            // el zoom. Cada vez (Rodrigo, 2026-08-16).
                            val targetZoom = maxOf(map.cameraPosition.zoom, ZOOM_PIEDRA)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(tapped.lat, tapped.lon), targetZoom))
                        }
                    }
                    onBlockSelected(tapped)
                }
            }
        }
    }

    // «Ver en mapa» desde la GUÍA: centra en la piedra y enseña su mini-ficha, SIN abrir la ficha completa.
    val focusBlockId by viewModel.focusBlockId.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(focusBlockId, mapRef.value, blocks) {
        val id = focusBlockId ?: return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val b = blocks.firstOrNull { it.id == id } ?: return@LaunchedEffect
        collapsedSectors = collapsedSectors - setOfNotNull(b.sectorBlockId)
        runCatching { map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(b.lat, b.lon), ZOOM_PIEDRA)) }
        miniBlock = b
        viewModel.consumeFocusBlock()
    }

    // Preview de muro activo: el del editor si está trazando, si no el del flujo crear.
    val activePreview = if (wallEdit.tracing) wallEdit.preview else bridge.wallPreview

    // Re-pinta markers cuando cambia el ghost, el preview del muro, se colapsa
    // un sector, te mueves o giras (brújula del punto azul).
    // Con una foto por confirmar, el mapa se centra en su punto: la estrella
    // puede caer fuera de pantalla y entonces no hay nada que juzgar.
    // OJO con la clave: tambien el MAPA. Al pasar a pantalla completa el mapa se
    // RECREA, asi que la orden de centrar llegaba cuando el mapa nuevo aun no
    // existia y se perdia -- el marcador estaba puesto, pero fuera de la vista
    // (Rodrigo: "solo veo el bosque").
    androidx.compose.runtime.LaunchedEffect(bridge.photoConfirm, mapRef.value) {
        val punto = bridge.photoConfirm ?: return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        runCatching {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(punto.first, punto.second), 16.5))
        }
    }

    androidx.compose.runtime.LaunchedEffect(bridge.correctionGhost, visibleMarkers, activePreview, userLoc, deviceHeading, gradeDimmedBlockIds) {
        val map = mapRef.value ?: return@LaunchedEffect
        placer.place(ctx, map, visibleMarkers, bridge.correctionGhost, userLoc, activePreview, deviceHeading, gradeDimmedBlockIds) { tapped ->
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
    val gradeDimmedState = androidx.compose.runtime.rememberUpdatedState(gradeDimmedBlockIds)
    androidx.compose.runtime.LaunchedEffect(mapRef.value) {
        val map = mapRef.value ?: return@LaunchedEffect
        var lastClusterZoom = map.cameraPosition.zoom
        // Pintado INMEDIATO al quedar el mapa listo: si las piedras/parkings ya
        // habían llegado del servidor ANTES de que el mapa terminara de
        // inicializarse, el LaunchedEffect de arriba se encontraba con
        // mapRef.value=null y se saltaba en silencio — y hasta aquí solo se
        // repintaba al mover la cámara (zoom/pan), nunca al abrir la escuela.
        // Reportado por un usuario real en la vc96 (Rodrigo, 2026-08-22:
        // "al entrar a una escuela no le aparecen las piedras y parkings
        // directamente").
        if (visibleState.value.isNotEmpty()) {
            placer.place(ctx, map, visibleState.value, ghostState.value,
                userLoc, previewState.value, gradeDimmedBlockIds = gradeDimmedState.value) { tapped ->
                if (bridge.correctionMode) bridge.handleMarkerTapForCorrection(tapped)
                else onBlockTap(tapped)
            }
        }
        map.addOnCameraIdleListener {
            val zoomChanged = kotlin.math.abs(map.cameraPosition.zoom - lastClusterZoom) > 0.4
            if (zoomChanged || (map.annotations.isEmpty() && visibleState.value.isNotEmpty())) {
                // Re-pinta al cambiar el zoom (los clústeres dependen de él) y
                // como vigilante anti-desapariciones del SDK.
                lastClusterZoom = map.cameraPosition.zoom
                placer.place(ctx, map, visibleState.value, ghostState.value,
                    userLoc, previewState.value, gradeDimmedBlockIds = gradeDimmedState.value) { tapped ->
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
                    placer.place(ctx, map, visibleMarkers, bridge.correctionGhost, userLoc, activePreview, gradeDimmedBlockIds = gradeDimmedBlockIds) { tapped ->
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
    val flowBanners: @Composable () -> Unit = { ProposalFlowBanners(bridge, wallEdit) }

    // MapView con overlays (+ PROPONER, expandir/cerrar, mini-ficha, dónde
    // estoy). Compartido entre el modo tarjeta (280 dp) y pantalla completa.
    val mapBox: @Composable (Modifier) -> Unit = { boxModifier ->
        Box(modifier = boxModifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapView(context.also { com.meteomontana.android.data.map.MapLibreInit.ensure(it) }, org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
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
                                placer.place(ctx, map, visibleMarkers, bridge.correctionGhost, userLoc, activePreview, gradeDimmedBlockIds = gradeDimmedBlockIds) { tapped ->
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
                                // La brujula del propio mapa: gira sola y al
                                // tocarla vuelve al norte. Antes se escondia al
                                // estar a norte, y por eso llegamos a dibujar
                                // otra al lado; ahora se queda siempre.
                                isCompassEnabled = true
                                setCompassFadeFacingNorth(false)
                                setCompassGravity(android.view.Gravity.TOP or android.view.Gravity.START)
                                // Debajo del boton de ampliar, no encima de el.
                                val d = ctx.resources.displayMetrics.density
                                setCompassMargins((12 * d).toInt(), (56 * d).toInt(), 0, 0)
                                // La brujula de serie es una flecha suelta que
                                // sobre el satelite no se ve: se sustituye por
                                // la nuestra, igual que la de iOS.
                                androidx.core.content.ContextCompat.getDrawable(
                                    ctx, com.meteomontana.android.R.drawable.ic_brujula_mapa
                                )?.let { setCompassImage(it) }
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
            // A su izquierda va el filtro de orientación (así la fila de sectores de arriba tiene todo el ancho).
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.guideTarget("detail_map.orientation")) { orientationControl() }
                Box(
                    modifier = Modifier
                        .guideTarget("detail_map.propose")
                        .clip(RoundedCornerShape(2.dp))
                        .background(terraFillColor())
                        .clickable(onClick = { bridge.proposeOpen = true })
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Text(stringResource(R.string.detail_propose), style = EyebrowTextStyle, color = Color.White)
                }
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
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.a11y_fullscreen_map),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp))
            }

            // Botonera lateral (SIEMPRE, estilo Radar — maqueta B aprobada):
            // topo↔satélite de un toque + capas con la FORMA real del marcador
            // (P cuadrada azul, piedra polígono terra, Z círculo verde);
            // apagado = capa oculta.
            if (!bridge.waitingMapTap && !bridge.correctionMode && !bridge.wallTracing) {
                SchoolMapSideButtons(
                    hiddenTypes = hiddenTypes,
                    onRecenter = { mapRef.value?.let { map -> fitSchoolBoundsCameraUpdate(visibleMarkers, null)?.let { runCatching { map.animateCamera(it) } } } },
                    onToggleStyle = { applyStyle(if (currentStyle == MapStyleOption.SATELLITE) MapStyleOption.TOPO else MapStyleOption.SATELLITE) },
                    onToggleLayer = { toggleLayer(it) },
                    onGoToMe = userLoc?.let { loc -> { mapRef.value?.let { map -> runCatching { map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.lat, loc.lon), 15.5)) } } } },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // Mini-ficha flotante de parking/sector: informa y da CÓMO LLEGAR
            // sin tapar el mapa (la ficha grande queda para las piedras).
            miniBlock?.let { mb ->
                MiniBlockCard(
                    block = mb,
                    stoneCount = blocks.count { it.sectorBlockId == mb.id },
                    collapsed = mb.id in collapsedSectors,
                    userLoc = userLoc,
                    onToggleStones = {
                        collapsedSectors =
                            if (mb.id in collapsedSectors) collapsedSectors - mb.id
                            else collapsedSectors + mb.id
                    },
                    onClose = { miniBlock = null },
                    approach = approachTo(mb.id),
                    onFollowApproach = onFollowApproach,
                    // Desde el parking se ofrecen los sectores, ordenados por lo
                    // que queda por andar. Tocar uno hace EXACTAMENTE lo mismo
                    // que tocarlo en el mapa (mismo camino de código: si algún
                    // día cambia el zoom o el desplegado, cambia en los dos).
                    sectores = if (mb.type == "PARKING")
                        com.meteomontana.android.domain.util.SectoresDesdeParking
                            .calcular(mb, blocks)
                    else emptyList(),
                    onSectorClick = { sector -> onBlockTap(sector) },
                    onOpenStone = { onBlockTap(mb) },
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
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

    // Altura del mapa en la ficha (como iOS): lo que sobra bajo cabecera, pestañas y filtros. Antes eran 280 fijos y
    // quedaba medio móvil vacío debajo. Limitada para que ni se quede diminuto ni se coma la pantalla entera.
    val cardMapHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 400).coerceIn(300, 560).dp

    Column(modifier = Modifier.fillMaxWidth()) {

        flowBanners()

        // SECTORES y PARKINGS arriba, cada uno en su fila y sin rótulos (S / P): tocar uno lleva el mapa allí. Tocar un
        // sector hace LO MISMO que tocarlo en el mapa (acercarse y desplegar sus piedras): un solo camino de código.
        // La ficha con "CÓMO LLEGAR" se abre con la mini-ficha, igual que al tocar el marcador.
        SchoolPlacesRows(
            blocks = blocks,
            userLoc = userLoc,
            onParking = { miniBlock = it; flyToParkingZone(it) },
            onSector = { onBlockTap(it) }
        )

        filtersContent()

        if (!fullscreenMap) {
            mapBox(Modifier.fillMaxWidth().height(cardMapHeight))
        }

        // Aproximaciones justo debajo del mapa, como en iOS (SchoolMapSection.swift → ApproachesSection).
        contenidoTrasMapa()
    }

}
