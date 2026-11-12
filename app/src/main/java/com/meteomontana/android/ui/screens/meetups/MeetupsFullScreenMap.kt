package com.meteomontana.android.ui.screens.meetups

import com.meteomontana.android.data.map.withMapLibre

import com.meteomontana.android.util.AppText
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
import androidx.compose.ui.res.stringResource

// Mapa de quedadas a pantalla completa y los dibujos de sus marcadores.

/**
 * Mapa de Quedadas a pantalla completa: mismo tratamiento que el mapa de
 * Escuelas (satélite/topo, brújula, botón cerrar) + filtros de MODALIDAD y
 * distancia que solo afectan a lo que se ve en el mapa (Álvaro, 2026-09-03).
 */
@Composable
internal fun FullScreenMeetupsMap(
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
        MapView(
            context.withMapLibre(),
            org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        ).apply {
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
                .title(AppText.get(R.string.meetups_map_panel_v4_tu_ubicacion)).icon(iconFactory.fromBitmap(createUserDot())))
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
                    contentDescription = stringResource(R.string.meetups_map_panel_v2_cerrar_pantalla_completa),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 50.dp, start = Spacing.sm),
                    onClick = onDismiss
                )
                RoundMapIconButton(
                    icon = Icons.Outlined.Layers,
                    contentDescription = stringResource(R.string.meetups_map_panel_topografico_satelite),
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
                                Text(stringResource(R.string.meetups_map_panel_v2_1_s_quedada_2_s, group.count, if (group.count != 1) "s" else "", if (group.count != 1) "s" else ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Terra)
                                    .clickable { onSchoolSelected(group.schoolId) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(R.string.meetups_map_panel_v2_ver), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
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
                        Text(stringResource(R.string.meetups_map_panel_v2_dist), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DisciplineFilterPill(stringResource(R.string.w_all), mapDistanceKm == null) { onMapDistanceKmChange(null) }
                        DisciplineFilterPill("50 km", mapDistanceKm == 50) { onMapDistanceKmChange(50) }
                        DisciplineFilterPill("100 km", mapDistanceKm == 100) { onMapDistanceKmChange(100) }
                        DisciplineFilterPill("200 km", mapDistanceKm == 200) { onMapDistanceKmChange(200) }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.meetups_map_panel_v2_modalidad), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DisciplineFilterPill(stringResource(R.string.w_both), disciplineFilter == null) { onDisciplineFilterChange(null) }
                    DisciplineFilterPill(stringResource(R.string.w_boulder), disciplineFilter == "BOULDER") { onDisciplineFilterChange("BOULDER") }
                    DisciplineFilterPill(stringResource(R.string.w_route), disciplineFilter == "ROUTE") { onDisciplineFilterChange("ROUTE") }
                }
            }
        }
    }
}

internal fun zoomForKm(km: Double): Double = when {
    km <= 25  -> 10.0
    km <= 50  -> 9.0
    km <= 100 -> 8.0
    km <= 200 -> 7.0
    km <= 500 -> 6.0
    else      -> 5.0
}

internal fun createSchoolBadge(name: String, count: Int): Bitmap {
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

internal fun createUserDot(): Bitmap {
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
