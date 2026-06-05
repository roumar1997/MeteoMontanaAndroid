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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.meteomontana.android.data.api.dto.BlockDto
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private enum class MapStyleOption(val label: String) {
    SATELLITE("Satélite"),
    TOPO("Topográfico")
}

private fun styleJsonFor(style: MapStyleOption): String = when (style) {
    MapStyleOption.SATELLITE -> """{"version":8,"sources":{"sat":{"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"Tiles © Esri"}},"layers":[{"id":"sat","type":"raster","source":"sat"}]}"""
    MapStyleOption.TOPO      -> """{"version":8,"sources":{"topo":{"type":"raster","tiles":["https://a.tile.opentopomap.org/{z}/{x}/{y}.png","https://b.tile.opentopomap.org/{z}/{x}/{y}.png","https://c.tile.opentopomap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenTopoMap (CC-BY-SA)"}},"layers":[{"id":"topo","type":"raster","source":"topo"}]}"""
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
    blocks: List<BlockDto>,
    viewModel: SchoolDetailViewModel,
    onMyProposals: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Estado del flujo de propuesta
    var proposeOpen    by remember { mutableStateOf(false) }
    var waitingMapTap  by remember { mutableStateOf(false) }

    // Callback que el flujo registra para recibir el tap en el mapa
    var mapTapCallback by remember { mutableStateOf<((Double, Double) -> Unit)?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {

        // ── Toggle "MAPA DE LA ESCUELA" ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(2.dp))
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
                    Text(if (expanded) "▲" else "▼",
                        color = MaterialTheme.colorScheme.background,
                        style = MaterialTheme.typography.labelLarge)
                    Text("MAPA DE LA ESCUELA",
                        color = MaterialTheme.colorScheme.background,
                        style = EyebrowTextStyle)
                }
                Text("${blocks.size} elementos",
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium)
            }
        }

        if (expanded) {
            InnerMap(
                centerLat     = centerLat,
                centerLon     = centerLon,
                blocks        = blocks,
                viewModel     = viewModel,
                onMyProposals = onMyProposals,
                waitingMapTap = waitingMapTap,
                onProposeClick = { proposeOpen = true },
                onCancelTap   = {
                    waitingMapTap = false
                    proposeOpen = false
                    mapTapCallback = null
                },
                onMapTapped   = { lat, lon ->
                    mapTapCallback?.invoke(lat, lon)
                    waitingMapTap = false
                }
            )
        }
    }

    // ── Flujo de propuesta (dialogs) ──────────────────────────────────────
    if (proposeOpen) {
        ProposeContributionFlow(
            schoolName      = "",
            waitingForTap   = waitingMapTap,
            onStartWaitingTap = { waitingMapTap = true },
            onMapTap        = { cb -> mapTapCallback = cb },
            onDismiss       = {
                proposeOpen = false
                waitingMapTap = false
                mapTapCallback = null
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
    blocks: List<BlockDto>,
    viewModel: SchoolDetailViewModel,
    onMyProposals: () -> Unit,
    waitingMapTap: Boolean,
    onProposeClick: () -> Unit,
    onCancelTap: () -> Unit,
    onMapTapped: (Double, Double) -> Unit
) {
    val ctx = LocalContext.current
    var currentStyle by remember { mutableStateOf(MapStyleOption.SATELLITE) }
    val mapViewRef   = remember { mutableStateOf<MapView?>(null) }
    val mapRef       = remember { mutableStateOf<MapLibreMap?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Bloque seleccionado (para popup) y bloque al que añadir vías
    var selectedBlock by remember { mutableStateOf<BlockDto?>(null) }
    var addingLinesTo by remember { mutableStateOf<BlockDto?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewRef.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START   -> mv.onStart()
                Lifecycle.Event.ON_RESUME  -> mv.onResume()
                Lifecycle.Event.ON_PAUSE   -> mv.onPause()
                Lifecycle.Event.ON_STOP    -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.apply { onPause(); onStop(); onDestroy() }
            mapViewRef.value = null
            mapRef.value = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Selector de estilo
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapStyleOption.entries.forEach { option ->
                StyleChip(option.label, currentStyle == option) {
                    if (currentStyle != option) {
                        currentStyle = option
                        mapViewRef.value?.getMapAsync { map ->
                            map.setStyle(Style.Builder().fromJson(styleJsonFor(option))) {
                                placeMarkers(ctx, map, blocks) { tapped ->
                                    selectedBlock = tapped
                                }
                            }
                        }
                    }
                }
            }
        }

        // Banner "PULSA EN EL MAPA" cuando estamos esperando tap
        if (waitingMapTap) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Terra)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ℹ PULSA EN EL MAPA DONDE QUIERES AÑADIR EL PARKING",
                    style = EyebrowTextStyle,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    " ✕",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable(onClick = onCancelTap)
                )
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
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(centerLat, centerLon))
                                    .zoom(15.0).build()
                                placeMarkers(ctx, map, blocks) { tapped ->
                                    selectedBlock = tapped
                                }
                            }
                            map.uiSettings.apply {
                                isRotateGesturesEnabled = false
                                isTiltGesturesEnabled   = false
                            }
                            map.addOnMapClickListener { point ->
                                if (waitingMapTap) {
                                    onMapTapped(point.latitude, point.longitude)
                                    true
                                } else {
                                    // Tap fuera de marker → cierra popup
                                    selectedBlock = null
                                    false
                                }
                            }
                        }
                        onStart(); onResume()
                    }
                },
                update = { _ ->
                    mapRef.value?.addOnMapClickListener { point ->
                        if (waitingMapTap) {
                            onMapTapped(point.latitude, point.longitude)
                            true
                        } else {
                            selectedBlock = null
                            false
                        }
                    }
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
                Text("+ PROPONER", style = EyebrowTextStyle, color = Color.White)
            }

        }
    }

    // Dialog de detalles (foto + líneas + vías + CÓMO LLEGAR).
    // No incluye BORRAR — esa acción vive en el panel admin (FullScreenMapDialog).
    selectedBlock?.let { block ->
        BlockDetailDialog(
            block = block,
            onAddLines = if (block.type == "BLOCK") ({
                addingLinesTo = block
                selectedBlock = null
            }) else null,
            onDismiss = { selectedBlock = null }
        )
    }

    // Flujo "+ AÑADIR VÍAS" a una piedra existente
    addingLinesTo?.let { block ->
        AddLinesFlow(
            block = block,
            viewModel = viewModel,
            onDismiss = { addingLinesTo = null },
            onSuccess = {
                addingLinesTo = null
                successMessage = "Propuesta enviada. Un admin la revisará en 24-48h."
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
                        Text("CERRAR", style = EyebrowTextStyle,
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

// Mapa de markers activos para poder mapear marker → BlockDto al tocar
private val markerBlockMap = mutableMapOf<Long, BlockDto>()

private fun placeMarkers(
    ctx: android.content.Context,
    map: MapLibreMap,
    blocks: List<BlockDto>,
    onBlockTap: (BlockDto) -> Unit
) {
    map.clear()
    markerBlockMap.clear()

    val iconFactory = IconFactory.getInstance(ctx)

    blocks.forEach { b ->
        val icon = when (b.type.uppercase()) {
            "PARKING" -> iconFactory.fromBitmap(parkingBitmap())
            "ZONE"    -> iconFactory.fromBitmap(zoneBitmap())
            else      -> iconFactory.fromBitmap(blockBitmap(b.name))
        }
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(b.lat, b.lon))
                .icon(icon)
                .title(b.name)
        )
        markerBlockMap[marker.id] = b
    }

    map.setOnMarkerClickListener { marker ->
        markerBlockMap[marker.id]?.let { onBlockTap(it) }
        true   // consumimos el evento: no se abre el InfoWindow por defecto
    }
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
    sizeDp = 40
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
