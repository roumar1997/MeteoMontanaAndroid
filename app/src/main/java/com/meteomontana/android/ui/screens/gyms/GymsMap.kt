package com.meteomontana.android.ui.screens.gyms

import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationDisabled
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meteomontana.android.R
import com.meteomontana.android.data.map.withMapLibre
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymLabelPlanner
import com.meteomontana.android.domain.model.gym.GymMapItem
import com.meteomontana.android.domain.model.gym.LabelCandidate
import com.meteomontana.android.ui.components.MapViewLifecycleEffect
import com.meteomontana.android.ui.components.SideMapButton
import com.meteomontana.android.ui.components.userDotBitmap
import com.meteomontana.android.ui.screens.schools.DARK_RASTER_STYLE
import com.meteomontana.android.ui.screens.schools.OSM_RASTER_STYLE
import com.meteomontana.android.ui.screens.schools.SATELLITE_RASTER_STYLE
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.abs
import kotlin.math.max

/**
 * Mapa de rocódromos (MapLibre): un marcador por ficha o un grupo con su número. La agrupación sale de
 * [cluster] (`GymClusterer` de shared) para que iOS y Android agrupen igual. Tocar un grupo acerca la cámara a
 * sus miembros; tocar un marcador lo selecciona ([onSelect]) y la pantalla enseña la tarjeta de cristal.
 */
@Composable
internal fun GymsMap(
    gyms: List<Gym>,
    cluster: (List<Gym>, Double) -> List<GymMapItem>,
    userLat: Double?,
    userLon: Double?,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    mapState: GymsMapState,
    onCenterChange: (lat: Double, lon: Double) -> Unit,
    onRequestLocation: () -> Unit,
    locationPermission: Boolean,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var zoom by remember { mutableDoubleStateOf(mapState.savedCamera?.zoom ?: SPAIN_ZOOM) }
    var styleTick by remember { mutableIntStateOf(0) }
    val latestGyms by rememberUpdatedState(gyms)
    val latestUser by rememberUpdatedState(userLat to userLon)
    val onSelectNow by rememberUpdatedState(onSelect)
    val onCenterNow by rememberUpdatedState(onCenterChange)

    MapViewLifecycleEffect(mapViewRef) { mapRef.value = null }
    GymsMapEffects(
        map = mapRef.value, dark = dark, gyms = gyms, zoom = zoom, selectedId = selectedId, styleTick = styleTick,
        user = userLat to userLon, cluster = cluster, mapState = mapState,
        onStyleLoad = { styleTick++ }, onSelect = { onSelectNow(it) }
    )

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                buildGymsMapView(
                    context, dark, mapState, { latestUser }, { latestGyms },
                    onReady = { map -> zoom = map.cameraPosition.zoom; mapRef.value = map; styleTick++ },
                    onCamera = { z, lat, lon ->
                        if (abs(z - zoom) > 0.01) { zoom = z }
                        onCenterNow(lat, lon)
                    },
                    onTapEmpty = { onSelectNow(null) }
                ).also { mapViewRef.value = it }
            }
        )
        // Mismos botones que el mapa de Escuelas: capas (satélite/topográfico) y ubicación.
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SideMapButton(active = true, onClick = { mapState.isSatellite = !mapState.isSatellite }) {
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = stringResource(R.string.map_topo) + "/" + stringResource(R.string.map_satellite),
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)
                )
            }
            LocateButton(userLat, userLon, mapRef.value, zoom, locationPermission, onRequestLocation)
        }
    }
}

/** Efectos del mapa: estilo claro/oscuro, marcadores (agrupación y selección), toques y encuadre. */
@Composable
private fun GymsMapEffects(
    map: MapLibreMap?,
    dark: Boolean,
    gyms: List<Gym>,
    zoom: Double,
    selectedId: Long?,
    styleTick: Int,
    user: Pair<Double?, Double?>,
    cluster: (List<Gym>, Double) -> List<GymMapItem>,
    mapState: GymsMapState,
    onStyleLoad: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val markers = remember { mutableListOf<Marker>() }
    var itemsById by remember { mutableStateOf<Map<String, GymMapItem>>(emptyMap()) }
    val freeLabel = stringResource(R.string.gym_free_badge)
    val styleLoaded by rememberUpdatedState(onStyleLoad)
    val selectNow by rememberUpdatedState(onSelect)
    val clusterNow by rememberUpdatedState(cluster)

    LaunchedEffect(dark, map, mapState.isSatellite) {
        val m = map ?: return@LaunchedEffect
        val pos = m.cameraPosition
        m.setStyle(Style.Builder().fromJson(gymMapStyle(mapState.isSatellite, dark))) { styleLoaded() }
        m.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
    }

    LaunchedEffect(gyms, zoom, selectedId, map, styleTick, user) {
        val m = map ?: return@LaunchedEffect
        markers.forEach { m.removeMarker(it) }
        markers.clear()
        val (uLat, uLon) = user
        if (uLat != null && uLon != null) {
            val dot = IconFactory.getInstance(ctx).fromBitmap(userDotBitmap())
            markers += m.addMarker(MarkerOptions().position(LatLng(uLat, uLon)).icon(dot))
        }
        val items = clusterNow(gyms, zoom)
        itemsById = items.associateBy { it.id }
        val labelled = labelledIds(ctx, m, items, zoom, selectedId)
        items.forEach { markers += m.addMarker(markerFor(ctx, m, it, dark, selectedId, freeLabel, it.id in labelled)) }
    }

    LaunchedEffect(itemsById, map) {
        val m = map ?: return@LaunchedEffect
        m.setOnMarkerClickListener { marker ->
            val item = itemsById[marker.snippet] ?: return@setOnMarkerClickListener false
            if (item.isCluster) zoomIntoCluster(ctx, m, item) else selectNow(item.gyms.first().id)
            true
        }
    }

    // Encuadre: cuando cambia QUÉ fichas se ven (filtros, capa) la cámara las abarca; no al cambiar solo la selección.
    LaunchedEffect(gyms, map) {
        val m = map ?: return@LaunchedEffect
        val ids = gyms.map { it.id }.toSet()
        if (ids == mapState.fittedIds) return@LaunchedEffect
        mapState.fittedIds = ids
        fitTo(ctx, m, gyms)
    }
}

/** Estilo del mapa: el mismo que Escuelas (satélite por defecto; topográfico claro u oscuro según el tema). */
private fun gymMapStyle(satellite: Boolean, dark: Boolean): String = when {
    satellite -> SATELLITE_RASTER_STYLE
    dark -> DARK_RASTER_STYLE
    else -> OSM_RASTER_STYLE
}

/** Ids de los marcadores sueltos que enseñan nombre: solo con zoom cercano y sin pisarse (ver [GymLabelPlanner]). */
private fun labelledIds(ctx: Context, map: MapLibreMap, items: List<GymMapItem>, zoom: Double, selectedId: Long?): Set<String> {
    if (zoom < GymLabelPlanner.MIN_ZOOM) return emptySet()
    val candidates = items.map { item ->
        val p = map.projection.toScreenLocation(LatLng(item.lat, item.lon))
        val single = if (item.isCluster) null else item.gyms.first()
        LabelCandidate(item.id, p.x, p.y, single?.name?.length ?: 0, priority = single?.id == selectedId)
    }
    return GymLabelPlanner.plan(candidates, ctx.resources.displayMetrics.density)
}

private fun markerFor(
    ctx: Context, map: MapLibreMap, item: GymMapItem, dark: Boolean, selectedId: Long?, freeLabel: String, withName: Boolean
): MarkerOptions {
    val single = if (item.isCluster) null else item.gyms.first()
    val icon = if (single == null) GymMarkerIcons.cluster(ctx, item.count, dark)
    else GymMarkerIcons.pin(ctx, single.kind, dark, single.id == selectedId, freeLabel, if (withName) single.name else null)
    var pos = LatLng(item.lat, item.lon)
    if (withName) {
        // El nombre alarga el bitmap por abajo y MapLibre ancla la base: se baja el punto para que el círculo siga en su sitio.
        val p = map.projection.toScreenLocation(pos)
        pos = map.projection.fromScreenLocation(PointF(p.x, p.y + GymLabelPlanner.LABEL_EXTRA_DP * ctx.resources.displayMetrics.density))
    }
    return MarkerOptions().position(pos).icon(icon).snippet(item.id)
}

/** Crea el MapView: estilo, cámara inicial (la guardada, el usuario o España) y avisos de cámara y toques. */
private fun buildGymsMapView(
    context: Context,
    dark: Boolean,
    mapState: GymsMapState,
    user: () -> Pair<Double?, Double?>,
    gyms: () -> List<Gym>,
    onReady: (MapLibreMap) -> Unit,
    onCamera: (zoom: Double, lat: Double, lon: Double) -> Unit,
    onTapEmpty: () -> Unit
): MapView = MapView(context.withMapLibre(), MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
    onCreate(null)
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
        }
        false
    }
    getMapAsync { map ->
        map.uiSettings.isTiltGesturesEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.setStyle(Style.Builder().fromJson(gymMapStyle(mapState.isSatellite, dark))) {
            val (uLat, uLon) = user()
            // Colocada la cámara (guardada o en el usuario), no se encuadra: se respeta donde estaba el usuario.
            if (placeInitialCamera(map, mapState.savedCamera, uLat, uLon)) mapState.fittedIds = gyms().map { it.id }.toSet()
            onReady(map)
        }
        map.addOnCameraIdleListener {
            val pos = map.cameraPosition
            mapState.savedCamera = pos
            pos.target?.let { onCamera(pos.zoom, it.latitude, it.longitude) }
        }
        map.addOnMapClickListener { onTapEmpty(); false }
    }
    onStart()
    onResume()
}

@Composable
private fun LocateButton(
    userLat: Double?,
    userLon: Double?,
    map: MapLibreMap?,
    zoom: Double,
    locationPermission: Boolean,
    onRequestLocation: () -> Unit
) {
    SideMapButton(
        active = true,
        onClick = {
            if (userLat != null && userLon != null && map != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLon), max(zoom, 11.0)), 400)
            } else onRequestLocation()
        }
    ) {
        Icon(
            if (locationPermission) Icons.Outlined.MyLocation else Icons.Outlined.LocationDisabled,
            contentDescription = stringResource(R.string.explore_location_button),
            tint = if (locationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}
