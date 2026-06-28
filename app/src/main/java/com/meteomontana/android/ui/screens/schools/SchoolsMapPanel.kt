package com.meteomontana.android.ui.screens.schools

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.meteomontana.android.domain.util.Geo

/**
 * Panel desplegable "VER MAPA" en la pantalla de escuelas.
 * Equivalente a `js/sectors/map-panel.js` de la PWA:
 *  - Toggle negro con eyebrow + chevron.
 *  - Cuando abre, muestra MapView con un marker por escuela visible
 *    (las que ya filtró el viewmodel).
 *  - Color del marker según score (verde / ámbar / rojo).
 *  - Tap en marker → tarjeta abajo con nombre, score, roca, estilo,
 *    km, "Cómo llegar" (Google Maps) y "Ver detalle".
 *  - Cuando cambian los filtros (= cambia `schools`), re-sincroniza
 *    markers automáticamente.
 *
 * Markers se re-pintan con un Bitmap generado a mano (diamante rotado
 * + score blanco encima) para parecerse al pin de la PWA.
 */
@Composable
fun SchoolsMapPanel(
    schools: List<School>,
    scoresById: Map<String, Int>,
    userLat: Double?,
    userLon: Double?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSchoolDetail: (String) -> Unit
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {

        // Toggle "VER MAPA" — barra clara con icono de mapa terracota (paridad iOS).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable(onClick = onToggle)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                Icons.Outlined.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                if (expanded) "OCULTAR MAPA" else "VER MAPA",
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (expanded) "▴" else "▾",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (expanded) {
            MapBody(
                schools = schools,
                scoresById = scoresById,
                userLat = userLat,
                userLon = userLon,
                onSchoolDetail = onSchoolDetail
            )
        }
    }
}

/* ─────────────────────────────────────────────────────────────────────────── */

@Composable
private fun MapBody(
    schools: List<School>,
    scoresById: Map<String, Int>,
    userLat: Double?,
    userLon: Double?,
    onSchoolDetail: (String) -> Unit
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Tiles oscuros si el tema actual es oscuro (CartoDB dark) — el mapa claro
    // era un fogonazo blanco en mitad de la UI oscura.
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val mapRef     = remember { mutableStateOf<MapLibreMap?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var selectedSchool by remember { mutableStateOf<School?>(null) }
    // ids de la última lista pintada: solo re-encuadramos la cámara cuando
    // cambia QUÉ escuelas se ven (filtros), no cuando llegan scores nuevos.
    val lastFittedIds = remember { mutableStateOf<Set<String>>(emptySet()) }
    // Etiquetas de nombre solo con zoom cercano (si no, se solapan).
    val labelsVisible = remember { mutableStateOf(false) }
    var isSatellite by remember { mutableStateOf(false) }

    com.meteomontana.android.ui.components.MapViewLifecycleEffect(mapViewRef) { mapRef.value = null }

    // Cambiar estilo topo/satélite
    LaunchedEffect(isSatellite, mapRef.value) {
        val map = mapRef.value ?: return@LaunchedEffect
        val pos = map.cameraPosition
        val style = when {
            isSatellite -> SATELLITE_RASTER_STYLE
            isDarkTheme -> DARK_RASTER_STYLE
            else -> OSM_RASTER_STYLE
        }
        map.setStyle(Style.Builder().fromJson(style))
        map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
    }

    // Re-sincronizar markers cuando cambian los filtros (= cambia `schools`)
    // o cuando el mapa ya está listo. Fit-bounds SOLO si cambió la lista de
    // escuelas; si solo llegaron scores nuevos, la cámara no se mueve.
    LaunchedEffect(schools, scoresById, mapRef.value, labelsVisible.value) {
        val map = mapRef.value ?: return@LaunchedEffect
        val ids = schools.map { it.id }.toSet()
        val listChanged = ids != lastFittedIds.value
        lastFittedIds.value = ids
        syncMarkers(
            ctx, map, schools, scoresById,
            showLabels = labelsVisible.value,
            fitBounds = listChanged,
            userLat = userLat, userLon = userLon
        ) { tappedSchool -> selectedSchool = tappedSchool }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(top = Spacing.xs)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(320.dp),
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
                        val styleJson = if (isDarkTheme) DARK_RASTER_STYLE else OSM_RASTER_STYLE
                        map.setStyle(Style.Builder().fromJson(styleJson)) {
                            // Con ubicación real: centramos cerca del usuario.
                            // Sin ella: España entera.
                            val center = if (userLat != null && userLon != null)
                                LatLng(userLat, userLon) else LatLng(40.4, -3.7)
                            val zoom = if (userLat != null && userLon != null) 8.0 else 5.0
                            map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                                .target(center).zoom(zoom).build()
                            lastFittedIds.value = schools.map { it.id }.toSet()
                            syncMarkers(
                                context, map, schools, scoresById,
                                showLabels = labelsVisible.value,
                                fitBounds = false,   // respetamos el centrado en el usuario
                                userLat = userLat, userLon = userLon
                            ) { tappedSchool -> selectedSchool = tappedSchool }
                        }
                        map.uiSettings.apply {
                            isRotateGesturesEnabled = true
                            isTiltGesturesEnabled   = false
                        }
                        // Etiquetas de nombre solo a partir de zoom 8.5 — más
                        // lejos se solaparían unas con otras.
                        map.addOnCameraIdleListener {
                            val shouldShow = map.cameraPosition.zoom >= 8.5
                            if (shouldShow != labelsVisible.value) labelsVisible.value = shouldShow
                        }
                    }
                    onStart()
                    onResume()
                }
            }
        )

        // Toggle topo/satélite
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MapStyleChip(label = "TOPO", selected = !isSatellite,
                onClick = { isSatellite = false })
            MapStyleChip(label = "SATÉLITE", selected = isSatellite,
                onClick = { isSatellite = true })
        }

        // Tarjeta inferior con el detalle del marker seleccionado.
        selectedSchool?.let { sel ->
            MarkerPreviewCard(
                school = sel,
                score = scoresById[sel.id],
                userLat = userLat,
                userLon = userLon,
                onClose = { selectedSchool = null },
                onSchoolDetail = { onSchoolDetail(sel.id); selectedSchool = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Spacing.md)
            )
        }
    }
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Markers                                                                    */
/* ─────────────────────────────────────────────────────────────────────────── */

private val activeMarkers = mutableListOf<Marker>()
private val markerSchoolBySnippet = mutableMapOf<String, String>()

/**
 * Borra los markers anteriores y crea uno por escuela visible. Hace fit-bounds
 * para que la cámara encuadre lo que el usuario está filtrando.
 */
private fun syncMarkers(
    ctx: android.content.Context,
    map: MapLibreMap,
    schools: List<School>,
    scoresById: Map<String, Int>,
    showLabels: Boolean,
    fitBounds: Boolean,
    userLat: Double?,
    userLon: Double?,
    onMarkerTap: (School) -> Unit
) {
    // Limpia los anteriores
    activeMarkers.forEach { map.removeMarker(it) }
    activeMarkers.clear()
    markerSchoolBySnippet.clear()
    map.setOnMarkerClickListener(null)

    // Punto azul con la posición del usuario (si la tenemos).
    if (userLat != null && userLon != null) {
        activeMarkers += map.addMarker(
            MarkerOptions()
                .position(LatLng(userLat, userLon))
                .icon(IconFactory.getInstance(ctx).fromBitmap(
                    com.meteomontana.android.ui.components.userDotBitmap()))
        )
    }

    if (schools.isEmpty()) return

    val boundsBuilder = LatLngBounds.Builder()
    schools.forEach { s ->
        val score = scoresById[s.id]
        val bmp = if (showLabels) diamondBitmap(score, s.name) else diamondBitmap(score, null)
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(s.lat, s.lon))
                .icon(IconFactory.getInstance(ctx).fromBitmap(bmp))
                .snippet(s.id)         // truco: snippet = id para mapear back
        )
        activeMarkers += marker
        markerSchoolBySnippet[s.id] = s.id
        boundsBuilder.include(LatLng(s.lat, s.lon))
    }

    // Recupera la escuela a partir del marker pulsado
    val schoolsById = schools.associateBy { it.id }
    map.setOnMarkerClickListener { marker ->
        val id = marker.snippet ?: return@setOnMarkerClickListener false
        schoolsById[id]?.let(onMarkerTap)
        true   // consumimos el evento; evita que MapLibre abra su infoWindow por defecto
    }

    if (fitBounds) runCatching {
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 48), 400)
    }
}


/** Color hex del pin según score, igual que la PWA (`scoreColor` en map-panel.js). */
private fun pinColorHex(score: Int?): String = when {
    score == null -> "#888888"
    score >= 70   -> "#4A7C59"
    score >= 50   -> "#C8843A"
    else          -> "#B94040"
}

/**
 * Genera el pin diamante con el score blanco encima y el nombre de la escuela
 * debajo (con halo blanco para que se lea sobre el mapa). Se construye en
 * código porque MapLibre no acepta vistas Compose como icon, sólo Bitmaps.
 */
private fun diamondBitmap(score: Int?, name: String?): Bitmap {
    val pinPx = 64
    val label = when {
        name == null -> null
        name.length > 16 -> name.take(15).trimEnd() + "…"
        else -> name
    }

    // Paints del nombre: halo blanco grueso debajo + texto tinta encima.
    val nameHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = AndroidColor.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val nameInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#1A1A1A".toColorInt()
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val nameWidth = if (label != null) nameHalo.measureText(label) + 12f else 0f
    val widthPx = maxOf(pinPx, nameWidth.toInt())
    val nameHeightPx = if (label != null) 28 else 0
    val bmp = Bitmap.createBitmap(widthPx, pinPx + nameHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val color = pinColorHex(score).toColorInt()

    // Sombra suave bajo el diamante
    val cx = widthPx / 2f
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.argb(60, 0, 0, 0)
    }
    canvas.drawOval(RectF(cx - 22f, pinPx - 16f, cx + 22f, pinPx - 6f), shadow)

    // Diamante: cuadrado rotado 45º, esquina inferior es la "punta"
    val side = pinPx * 0.55f
    val cy = pinPx / 2f - 4f
    canvas.save()
    canvas.rotate(45f, cx, cy)
    val rect = RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawRoundRect(rect, 6f, 6f, fill)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        this.color = AndroidColor.WHITE
    }
    canvas.drawRoundRect(rect, 6f, 6f, stroke)
    canvas.restore()

    // Score blanco centrado
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(score?.toString() ?: "·", cx, cy + 6f, txt)

    // Nombre debajo del pin (halo primero, tinta encima)
    if (label != null) {
        val nameY = pinPx + nameHeightPx - 8f
        canvas.drawText(label, cx, nameY, nameHalo)
        canvas.drawText(label, cx, nameY, nameInk)
    }

    return bmp
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Tarjeta del marker seleccionado                                            */
/* ─────────────────────────────────────────────────────────────────────────── */

@Composable
private fun MarkerPreviewCard(
    school: School,
    score: Int?,
    userLat: Double?,
    userLon: Double?,
    onClose: () -> Unit,
    onSchoolDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val distKm = haversineKm(userLat, userLon, school.lat, school.lon)
    val scoreColor = pinColorHex(score)

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                school.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (score != null) "$score/100" else "—",
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color(scoreColor.toColorInt())
            )
            Text(
                " ✕",
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(start = Spacing.sm),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Row(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            school.rockType?.let { Tag(it) }
            school.style?.let    { Tag(it) }
            distKm?.let          { Tag("${it.toInt()} km") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedAction(
                text = "CÓMO LLEGAR",
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${school.lat},${school.lon}")
                    )
                    runCatching { ctx.startActivity(intent) }
                },
                modifier = Modifier.weight(1f)
            )
            FilledAction(
                text = "VER DETALLE ▸",
                onClick = onSchoolDetail,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(
            text.uppercase(),
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OutlinedAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.onBackground, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun FilledAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.onBackground)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.background)
    }
}

/* ─────────────────────────────────────────────────────────────────────────── */

/** Estilo MapLibre con tiles raster de OSM. Mismo origen que la PWA. */
private val OSM_RASTER_STYLE = """
{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://a.tile.openstreetmap.org/{z}/{x}/{y}.png","https://b.tile.openstreetmap.org/{z}/{x}/{y}.png","https://c.tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenStreetMap"}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}
""".trimIndent()

/** Tiles oscuros (CartoDB dark matter) para cuando el tema de la app es oscuro. */
private val DARK_RASTER_STYLE = """
{"version":8,"sources":{"carto":{"type":"raster","tiles":["https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png","https://b.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png","https://c.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenStreetMap © CARTO"}},"layers":[{"id":"carto","type":"raster","source":"carto"}]}
""".trimIndent()

private val SATELLITE_RASTER_STYLE = """
{"version":8,"sources":{"esri":{"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"© Esri"}},"layers":[{"id":"esri","type":"raster","source":"esri"}]}
""".trimIndent()

@Composable
private fun MapStyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline,
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

/** Distancia aproximada en km. Null si no tenemos ubicación del usuario. */
private fun haversineKm(lat1: Double?, lon1: Double?, lat2: Double, lon2: Double): Double? {
    if (lat1 == null || lon1 == null) return null
    return Geo.haversineKm(lat1, lon1, lat2, lon2)
}
