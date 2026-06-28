package com.meteomontana.android.ui.screens.meetups

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Map
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meteomontana.android.domain.model.Meetup
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
    val groups = remember(meetups) {
        meetups.filter { it.schoolLat != null && it.schoolLon != null }
            .groupBy { it.schoolId }
            .map { (schoolId, list) ->
                SchoolMeetupGroup(
                    schoolId = schoolId,
                    schoolName = list.first().schoolName ?: schoolId,
                    lat = list.first().schoolLat!!,
                    lon = list.first().schoolLon!!,
                    count = list.size,
                    meetups = list
                )
            }
    }

    Column {
        // Toggle bar — visible, con chevron
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline)
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
            visible = expanded && groups.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            MeetupsMapView(
                groups = groups,
                userLat = userLat,
                userLon = userLon,
                maxDistanceKm = maxDistanceKm,
                onSchoolSelected = onSchoolSelected
            )
        }
    }
}

@Composable
private fun MeetupsMapView(
    groups: List<SchoolMeetupGroup>,
    userLat: Double?,
    userLon: Double?,
    maxDistanceKm: Int?,
    onSchoolSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedGroup by remember { mutableStateOf<SchoolMeetupGroup?>(null) }
    val markerToGroup = remember { mutableMapOf<Marker, SchoolMeetupGroup>() }
    var isSatellite by remember { mutableStateOf(false) }

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
        val json = """{"version":8,"sources":{"osm":{"type":"raster","tiles":["$url"],"tileSize":256}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}"""
        map.setStyle(Style.Builder().fromJson(json))
    }

    val mapView = remember {
        MapView(context).apply {
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
                applyStyle(map, false)
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
            if (groups.isEmpty() && userLat != null && userLon != null) {
                val zoom = if (maxDistanceKm != null) zoomForKm(maxDistanceKm.toDouble()) else 8.0
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(userLat, userLon), zoom))
            } else if (groups.size == 1 && userLat == null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(groups[0].lat, groups[0].lon), 10.0))
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
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

        // Toggle topo/satélite (esquina superior derecha)
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

        // Popup de la escuela seleccionada
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .clickable {
                            onSchoolSelected(group.schoolId)
                            selectedGroup = null
                        }
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(group.schoolName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium)
                        Text("${group.count} quedada${if (group.count != 1) "s" else ""} activa${if (group.count != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("VER ▸",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MapStyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg,
            fontWeight = FontWeight.Bold)
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
    val density = 2f
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
