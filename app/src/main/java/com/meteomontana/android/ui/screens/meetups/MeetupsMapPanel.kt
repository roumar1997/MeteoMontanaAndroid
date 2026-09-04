package com.meteomontana.android.ui.screens.meetups

import com.meteomontana.android.data.map.MapStyles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.util.Geo
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

data class SchoolMeetupGroup(
    val schoolId: String,
    val schoolName: String,
    val lat: Double,
    val lon: Double,
    val count: Int,
    val meetups: List<Meetup>
)

@Composable
fun MeetupsMapPanel(
    meetups: List<Meetup>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSchoolSelected: (String) -> Unit,
    userLat: Double? = null,
    userLon: Double? = null,
    maxDistanceKm: Int? = null
) {
    // Filtro SOLO del mapa (no toca la lista de quedadas de fuera) — mismo
    // criterio que el mapa de Escuelas, cuyos filtros de pantalla completa
    // tampoco afectan a nada fuera del propio mapa. Paridad con iOS
    // (Álvaro, 2026-09-03: "debería funcionar igual que el de escuelas").
    var disciplineFilter by remember { mutableStateOf<String?>(null) }
    var mapDistanceKm by remember { mutableStateOf<Int?>(null) }

    fun groupOf(list: List<Meetup>): List<SchoolMeetupGroup> {
        var filtered = list
        disciplineFilter?.let { d -> filtered = filtered.filter { it.discipline == d } }
        mapDistanceKm?.let { km ->
            if (userLat != null && userLon != null) {
                filtered = filtered.filter { m ->
                    val lat = m.schoolLat; val lon = m.schoolLon
                    lat != null && lon != null && Geo.haversineKm(userLat, userLon, lat, lon) <= km
                }
            }
        }
        return filtered.filter { it.schoolLat != null && it.schoolLon != null }
            .groupBy { it.schoolId }
            .map { (schoolId, l) ->
                SchoolMeetupGroup(
                    schoolId = schoolId,
                    schoolName = l.first().schoolName ?: schoolId,
                    lat = l.first().schoolLat!!,
                    lon = l.first().schoolLon!!,
                    count = l.size,
                    meetups = l
                )
            }
    }

    val groups = remember(meetups, disciplineFilter, mapDistanceKm) { groupOf(meetups) }
    var fullscreenMap by remember { mutableStateOf(false) }

    Column {
        // Toggle bar — botón flotante con borde, como el "VER MAPA" de Escuelas.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .clip(com.meteomontana.android.ui.theme.CumbrePillShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .border(1.dp, MaterialTheme.colorScheme.primary, com.meteomontana.android.ui.theme.CumbrePillShape)
                .clickable(onClick = onToggle)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(Icons.Outlined.Map, contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(
                if (expanded) "OCULTAR MAPA" else "VER MAPA DE QUEDADAS",
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (groups.isNotEmpty()) {
                Text(
                    "${groups.size} escuela${if (groups.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            if (groups.isNotEmpty() || (userLat != null && userLon != null)) {
                MeetupsMapView(
                    groups = groups,
                    userLat = userLat,
                    userLon = userLon,
                    maxDistanceKm = maxDistanceKm,
                    onSchoolSelected = onSchoolSelected,
                    onFullscreen = { fullscreenMap = true }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No hay quedadas con ubicación para mostrar en el mapa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (fullscreenMap) {
        FullScreenMeetupsMap(
            groups = groups,
            userLat = userLat,
            userLon = userLon,
            disciplineFilter = disciplineFilter,
            onDisciplineFilterChange = { disciplineFilter = it },
            mapDistanceKm = mapDistanceKm,
            onMapDistanceKmChange = { mapDistanceKm = it },
            onSchoolSelected = {
                onSchoolSelected(it)
                fullscreenMap = false
            },
            onDismiss = { fullscreenMap = false }
        )
    }
}

@Composable
private fun MeetupsMapView(
    groups: List<SchoolMeetupGroup>,
    userLat: Double?,
    userLon: Double?,
    maxDistanceKm: Int?,
    onSchoolSelected: (String) -> Unit,
    onFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedGroup by remember { mutableStateOf<SchoolMeetupGroup?>(null) }
    val markerToGroup = remember { mutableMapOf<Marker, SchoolMeetupGroup>() }
    // Satélite por defecto, paridad con el mapa de Escuelas (Álvaro, 2026-09-03).
    var isSatellite by remember { mutableStateOf(true) }

    fun tileUrl(satellite: Boolean, dark: Boolean): String = when {
        satellite -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        dark -> "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png"
        else -> "https://tile.opentopomap.org/{z}/{x}/{y}.png"
    }

    fun applyStyle(map: MapLibreMap, satellite: Boolean) {
        val dark = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val url = tileUrl(satellite, dark)
        map.setStyle(Style.Builder().fromJson(MapStyles.raster("osm", listOf(url))))
    }

    val mapView = remember {
        MapView(context, org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
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
                mapRef = map
                applyStyle(map, true)
                // Brújula igual que el mapa de Escuelas (Álvaro, 2026-09-03).
                map.uiSettings.apply {
                    isCompassEnabled = true
                    setCompassFadeFacingNorth(false)
                    setCompassGravity(android.view.Gravity.TOP or android.view.Gravity.START)
                    val d = context.resources.displayMetrics.density
                    setCompassMargins((12 * d).toInt(), (56 * d).toInt(), 0, 0)
                    androidx.core.content.ContextCompat.getDrawable(
                        context, R.drawable.ic_brujula_mapa
                    )?.let { setCompassImage(it) }
                }
                map.setOnMarkerClickListener { marker ->
                    markerToGroup[marker]?.let { group ->
                        selectedGroup = group
                    }
                    true
                }
                map.addOnMapClickListener {
                    selectedGroup = null
                    true
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(groups, mapRef, userLat, userLon, maxDistanceKm) {
        val map = mapRef ?: return@LaunchedEffect
        map.markers.forEach { map.removeMarker(it) }
        markerToGroup.clear()
        val iconFactory = IconFactory.getInstance(context)

        // Punto azul de ubicación
        if (userLat != null && userLon != null) {
            val dot = createUserDot()
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(userLat, userLon))
                    .title("Tu ubicación")
                    .icon(iconFactory.fromBitmap(dot))
            )
        }

        // Markers de escuelas con quedadas
        groups.forEach { group ->
            val bmp = createSchoolBadge(group.schoolName, group.count)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(group.lat, group.lon))
                    .title(group.schoolName)
                    .icon(iconFactory.fromBitmap(bmp))
            )
            markerToGroup[marker] = group
        }

        // Centrar: siempre encuadrar todos los markers visibles + tu ubicación
        val boundsBuilder = LatLngBounds.Builder()
        if (userLat != null && userLon != null) {
            boundsBuilder.include(LatLng(userLat, userLon))
        }
        groups.forEach { boundsBuilder.include(LatLng(it.lat, it.lon)) }
        try {
            if (groups.isNotEmpty()) {
                // Hay quedadas visibles: encuadrar tu ubicación + las quedadas
                // (como el mapa de escuelas), así SIEMPRE se ven los marcadores.
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
            } else if (userLat != null && userLon != null) {
                // Sin quedadas visibles: centrar en ti. Con filtro de distancia,
                // al zoom del radio (25km→cerca, 200km→lejos); si no, zoom cómodo.
                val zoom = if (maxDistanceKm != null) zoomForKm(maxDistanceKm.toDouble()) else 8.0
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLon), zoom))
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(40.4, -3.7), 6.0))
            }
        } catch (_: Exception) {
            val center = if (userLat != null && userLon != null) LatLng(userLat, userLon)
                         else if (groups.isNotEmpty()) LatLng(groups[0].lat, groups[0].lon)
                         else LatLng(40.4, -3.7)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 6.0))
        }
    }

    // Cambiar estilo cuando cambia isSatellite
    LaunchedEffect(isSatellite, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        val pos = map.cameraPosition
        applyStyle(map, isSatellite)
        map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
    }

    Box {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )

        // Pantalla completa (esquina superior izquierda) + capas (esquina
        // superior derecha) — mismo icono y sitio que el mapa de Escuelas.
        RoundMapIconButton(
            icon = Icons.Outlined.OpenInFull,
            contentDescription = "Pantalla completa",
            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm),
            onClick = onFullscreen
        )
        RoundMapIconButton(
            icon = Icons.Outlined.Layers,
            contentDescription = "Topográfico/Satélite",
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
            onClick = { isSatellite = !isSatellite }
        )

        // Popup de la escuela seleccionada
        selectedGroup?.let { group ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(Spacing.sm)
            ) {
                // Mini-ficha (mismo estilo que la de parkings/escuelas).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // Chip con el nº de quedadas de la escuela.
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${group.count}",
                            style = MaterialTheme.typography.titleMedium,
                            color = androidx.compose.ui.graphics.Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(group.schoolName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium)
                        Text("${group.count} quedada${if (group.count != 1) "s" else ""} activa${if (group.count != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                onSchoolSelected(group.schoolId)
                                selectedGroup = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("VER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White)
                    }
                    Text("✕",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { selectedGroup = null }
                            .padding(4.dp))
                }
            }
        }
    }
}

/** Botón circular flotante sobre el mapa — mismo estilo en escuelas/quedadas. */
@Composable
private fun RoundMapIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DisciplineFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(com.meteomontana.android.ui.theme.CumbrePillShape)
            .background(if (selected) Terra else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                com.meteomontana.android.ui.theme.CumbrePillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(label, style = EyebrowTextStyle,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Mapa de Quedadas a pantalla completa: mismo tratamiento que el mapa de
 * Escuelas (satélite/topo, brújula, botón cerrar) + filtros de MODALIDAD y
 * distancia que solo afectan a lo que se ve en el mapa (Álvaro, 2026-09-03).
 */
@Composable
private fun FullScreenMeetupsMap(
    groups: List<SchoolMeetupGroup>,
    userLat: Double?,
    userLon: Double?,
    disciplineFilter: String?,
    onDisciplineFilterChange: (String?) -> Unit,
    mapDistanceKm: Int?,
    onMapDistanceKmChange: (Int?) -> Unit,
    onSchoolSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedGroup by remember { mutableStateOf<SchoolMeetupGroup?>(null) }
    val markerToGroup = remember { mutableMapOf<Marker, SchoolMeetupGroup>() }
    var isSatellite by remember { mutableStateOf(true) }

    fun tileUrl(satellite: Boolean, dark: Boolean): String = when {
        satellite -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        dark -> "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png"
        else -> "https://tile.opentopomap.org/{z}/{x}/{y}.png"
    }
    fun applyStyle(map: MapLibreMap, satellite: Boolean) {
        val dark = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        map.setStyle(Style.Builder().fromJson(MapStyles.raster("osm", listOf(tileUrl(satellite, dark)))))
    }

    val mapView = remember {
        MapView(context, org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            getMapAsync { map ->
                mapRef = map
                applyStyle(map, true)
                // Brújula bajada bajo la isla en fullscreen, igual que Escuelas.
                map.uiSettings.apply {
                    isCompassEnabled = true
                    setCompassFadeFacingNorth(false)
                    setCompassGravity(android.view.Gravity.TOP or android.view.Gravity.START)
                    val d = context.resources.displayMetrics.density
                    setCompassMargins((12 * d).toInt(), (106 * d).toInt(), 0, 0)
                    androidx.core.content.ContextCompat.getDrawable(
                        context, R.drawable.ic_brujula_mapa
                    )?.let { setCompassImage(it) }
                }
                map.setOnMarkerClickListener { marker ->
                    markerToGroup[marker]?.let { selectedGroup = it }
                    true
                }
                map.addOnMapClickListener { selectedGroup = null; true }
            }
            onCreate(null); onStart(); onResume()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(groups, mapRef, userLat, userLon) {
        val map = mapRef ?: return@LaunchedEffect
        map.markers.forEach { map.removeMarker(it) }
        markerToGroup.clear()
        val iconFactory = IconFactory.getInstance(context)
        if (userLat != null && userLon != null) {
            map.addMarker(MarkerOptions().position(LatLng(userLat, userLon))
                .title("Tu ubicación").icon(iconFactory.fromBitmap(createUserDot())))
        }
        groups.forEach { group ->
            val marker = map.addMarker(
                MarkerOptions().position(LatLng(group.lat, group.lon)).title(group.schoolName)
                    .icon(iconFactory.fromBitmap(createSchoolBadge(group.schoolName, group.count)))
            )
            markerToGroup[marker] = group
        }
        val boundsBuilder = LatLngBounds.Builder()
        if (userLat != null && userLon != null) boundsBuilder.include(LatLng(userLat, userLon))
        groups.forEach { boundsBuilder.include(LatLng(it.lat, it.lon)) }
        try {
            if (groups.isNotEmpty()) {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
            } else if (userLat != null && userLon != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLon), 8.0))
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(40.4, -3.7), 6.0))
            }
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(isSatellite, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        val pos = map.cameraPosition
        applyStyle(map, isSatellite)
        map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                RoundMapIconButton(
                    icon = Icons.Outlined.CloseFullscreen,
                    contentDescription = "Cerrar pantalla completa",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 50.dp, start = Spacing.sm),
                    onClick = onDismiss
                )
                RoundMapIconButton(
                    icon = Icons.Outlined.Layers,
                    contentDescription = "Topográfico/Satélite",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 50.dp, end = Spacing.sm),
                    onClick = { isSatellite = !isSatellite }
                )

                selectedGroup?.let { group ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Terra),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${group.count}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(group.schoolName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("${group.count} quedada${if (group.count != 1) "s" else ""} activa${if (group.count != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Terra)
                                    .clickable { onSchoolSelected(group.schoolId) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("VER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Text("✕", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { selectedGroup = null }.padding(4.dp))
                        }
                    }
                }
            }

            // Filtros DISTANCIA + MODALIDAD, solo del mapa (Álvaro, 2026-09-03).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (userLat != null) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DIST.", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DisciplineFilterPill("Todas", mapDistanceKm == null) { onMapDistanceKmChange(null) }
                        DisciplineFilterPill("50 km", mapDistanceKm == 50) { onMapDistanceKmChange(50) }
                        DisciplineFilterPill("100 km", mapDistanceKm == 100) { onMapDistanceKmChange(100) }
                        DisciplineFilterPill("200 km", mapDistanceKm == 200) { onMapDistanceKmChange(200) }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MODALIDAD", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DisciplineFilterPill("Ambas", disciplineFilter == null) { onDisciplineFilterChange(null) }
                    DisciplineFilterPill("Bloque", disciplineFilter == "BOULDER") { onDisciplineFilterChange("BOULDER") }
                    DisciplineFilterPill("Vía", disciplineFilter == "ROUTE") { onDisciplineFilterChange("ROUTE") }
                }
            }
        }
    }
}

private fun zoomForKm(km: Double): Double = when {
    km <= 25  -> 10.0
    km <= 50  -> 9.0
    km <= 100 -> 8.0
    km <= 200 -> 7.0
    km <= 500 -> 6.0
    else      -> 5.0
}

private fun createSchoolBadge(name: String, count: Int): Bitmap {
    // 3f (antes 2f) → badge ~50% más grande y legible, a la par que iOS (M3b).
    val density = 3f
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 11f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    val label = "$name · $count"
    val textWidth = textPaint.measureText(label)
    val paddingH = 10f * density
    val paddingV = 5f * density
    val w = (textWidth + paddingH * 2).toInt()
    val h = (textPaint.textSize + paddingV * 2).toInt()
    val pointerH = 6f * density

    val bmp = Bitmap.createBitmap(w, h + pointerH.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#C2542D")
    }
    val r = 4f * density
    canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, bgPaint)

    // Pointer triangle
    val path = android.graphics.Path().apply {
        moveTo(w / 2f - 6 * density, h.toFloat())
        lineTo(w / 2f, h + pointerH)
        lineTo(w / 2f + 6 * density, h.toFloat())
        close()
    }
    canvas.drawPath(path, bgPaint)

    // Text
    val textY = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, paddingH, textY, textPaint)

    return bmp
}

private fun createUserDot(): Bitmap {
    val size = 24
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // White border
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1f, paint)

    // Blue fill
    paint.color = AndroidColor.parseColor("#4285F4")
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, paint)

    return bmp
}
