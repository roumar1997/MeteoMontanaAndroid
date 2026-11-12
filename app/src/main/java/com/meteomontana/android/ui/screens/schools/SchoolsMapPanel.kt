package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.data.map.withMapLibre

import com.meteomontana.android.util.CatalogLabels
import com.meteomontana.android.data.map.MapStyles

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.LocationDisabled
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.meteomontana.android.domain.util.Geo
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

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

/**
 * Estado del mapa que debe SOBREVIVIR al reciclado del item del `LazyColumn`
 * (el `MapBody` vive dentro de la lista y se destruye/recrea al scrollear).
 * Se recuerda en el propietario estable (`SchoolListScreen`) y se pasa aquí,
 * de modo que la pantalla NO ve tipos de MapLibre — quedan encapsulados.
 *  - [savedCamera]: la última cámara, para restaurarla al recrear el mapa.
 *  - [fittedIds]: ids ya encuadrados, para no re-encuadrar salvo cambio de filtro.
 */
class SchoolsMapState {
    val savedCamera: MutableState<CameraPosition?> = mutableStateOf(null)
    val fittedIds: MutableState<Set<String>> = mutableStateOf(emptySet())
}

@Composable
fun rememberSchoolsMapState(): SchoolsMapState = remember { SchoolsMapState() }

/**
 * Plan de cámara INICIAL cuando no hay ninguna guardada (primer abrir del mapa).
 * Función pura (sin tipos de MapLibre) → testeable en JVM.
 *  - 2+ escuelas → [fit] = true: encuadrar las visibles (igual que al filtrar).
 *  - 1 escuela → centrar en ella (zoom cercano).
 *  - 0 escuelas con ubicación → centrar en el usuario.
 *  - 0 escuelas sin ubicación → vista de España.
 */
data class MapCameraPlan(
    val lat: Double,
    val lon: Double,
    val zoom: Double,
    val fit: Boolean
)

fun planInitialCamera(schools: List<School>, userLat: Double?, userLon: Double?): MapCameraPlan {
    val single = schools.singleOrNull()
    return when {
        schools.size >= 2 -> MapCameraPlan(0.0, 0.0, 0.0, fit = true)
        single != null -> MapCameraPlan(single.lat, single.lon, 13.5, fit = false)
        userLat != null && userLon != null -> MapCameraPlan(userLat, userLon, 8.0, fit = false)
        else -> MapCameraPlan(40.4, -3.7, 5.0, fit = false)
    }
}

@Composable
fun SchoolsMapPanel(
    schools: List<School>,
    scoresById: Map<String, Int>,
    userLat: Double?,
    userLon: Double?,
    expanded: Boolean,
    onSchoolDetail: (String) -> Unit,
    // M2: cámara persistida por el LLAMANTE (SchoolListScreen, que NO se recicla al
    // scrollear). El mapa vive en un item de LazyColumn → al salir/entrar de pantalla
    // se DESTRUYE y recrea, y volvía a la cámara inicial. Con el estado en el llamante
    // (SchoolListScreen, que NO se recicla), el mapa reaparece donde lo dejaste y no
    // re-encuadra salvo cambio de filtro. Ver [SchoolsMapState].
    mapState: SchoolsMapState,
    // Filtros DISTANCIA/ESTILO: a pantalla completa hay demasiadas escuelas
    // para verlas bien sin filtrar, así que se ofrecen ahí también (Álvaro,
    // 2026-09-01) — mismo estado que la barra de filtros de la lista.
    distanceKm: Double?,
    onDistanceChange: (Double?) -> Unit,
    style: StyleFilter,
    onStyleChange: (StyleFilter) -> Unit,
    onRequestLocation: () -> Unit = {},
    locationPermission: Boolean = true
) {
    // Solo el cuerpo: la barra "VER MAPA / OCULTAR MAPA" va aparte ([MapToggleBar]), debajo del selector Escuelas /
    // Vías-Bloques, y el mapa se abre ENCIMA de él (como en iOS).
    if (!expanded) return
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {

        MapBody(
                schools = schools,
                scoresById = scoresById,
                userLat = userLat,
                userLon = userLon,
                onSchoolDetail = onSchoolDetail,
                mapState = mapState,
                distanceKm = distanceKm,
                onDistanceChange = onDistanceChange,
                style = style,
                onStyleChange = onStyleChange,
                onRequestLocation = onRequestLocation,
                locationPermission = locationPermission
            )
    }
}

/**
 * Barra "VER MAPA / OCULTAR MAPA": a todo el ancho y en su PROPIA línea, relleno terracota al abrirlo. Solo con el
 * icono dentro del selector pasaba desapercibido (Álvaro, 2026-10-14: "casi no se ve que existe").
 */
@Composable
fun MapToggleBar(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val terra = MaterialTheme.colorScheme.primary
    val fg = if (expanded) androidx.compose.ui.graphics.Color.White else terra
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (expanded) com.meteomontana.android.ui.theme.terraFillColor() else terra.copy(alpha = 0.10f))
            .border(1.dp, terra, androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.Map, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(
            if (expanded) stringResource(R.string.schools_hide_map) else stringResource(R.string.schools_view_map),
            style = EyebrowTextStyle, color = fg, modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription = null, tint = fg
        )
    }
}

/* ─────────────────────────────────────────────────────────────────────────── */

@Composable
internal fun MapBody(
    schools: List<School>,
    scoresById: Map<String, Int>,
    userLat: Double?,
    userLon: Double?,
    onSchoolDetail: (String) -> Unit,
    mapState: SchoolsMapState,
    distanceKm: Double?,
    onDistanceChange: (Double?) -> Unit,
    style: StyleFilter,
    onStyleChange: (StyleFilter) -> Unit,
    onRequestLocation: () -> Unit,
    locationPermission: Boolean
) {
    val savedCamera = mapState.savedCamera
    val lastFittedIds = mapState.fittedIds
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
    // (M2/M3: `lastFittedIds` llega como parámetro persistido por el padre.)
    // Etiquetas de nombre solo con zoom cercano (si no, se solapan).
    val labelsVisible = remember { mutableStateOf(false) }
    // Satélite por defecto al abrir, paridad con el mapa de detalle de
    // escuela (Álvaro, 2026-09-01: "que se abra en satélite por defecto").
    var isSatellite by remember { mutableStateOf(true) }
    var fullscreenMap by remember { mutableStateOf(false) }

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

    val mapBox: @Composable (Modifier) -> Unit = { boxModifier ->
    Box(modifier = boxModifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(
                    context.withMapLibre(),
                    org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true)
                ).apply {
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
                        // M2: fijar lastFittedIds SÍNCRONAMENTE al recrear el mapa, para
                        // que el LaunchedEffect no crea que "cambió la lista" y re-encuadre
                        // pisando la cámara restaurada (carrera del reciclado del LazyColumn).
                        lastFittedIds.value = schools.map { it.id }.toSet()
                        val styleJson = when {
                            isSatellite -> SATELLITE_RASTER_STYLE
                            isDarkTheme -> DARK_RASTER_STYLE
                            else -> OSM_RASTER_STYLE
                        }
                        map.setStyle(Style.Builder().fromJson(styleJson)) {
                            // Si al abrir el mapa la lista ya viene filtrada a UNA
                            // escuela (buscador), centramos en ELLA (como iOS). Si no,
                            // con ubicación real cerca del usuario; sin ella, España.
                            // M2: si hay cámara guardada (el mapa se recreó al scrollear),
                            // la restauramos → el mapa reaparece donde lo dejaste, no en
                            // el encuadre inicial.
                            // doFit = encuadrar las escuelas visibles (como al cambiar
                            // de filtro). Así el mapa al ABRIR la app muestra LO MISMO
                            // que tras filtrar a esa misma distancia (antes abría a un
                            // zoom fijo centrado en ti y no coincidía).
                            val restored = savedCamera.value
                            val doFit: Boolean
                            if (restored != null) {
                                map.cameraPosition = restored           // M2: donde lo dejaste
                                doFit = false
                            } else {
                                val plan = planInitialCamera(schools, userLat, userLon)
                                doFit = plan.fit
                                if (!plan.fit) {
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(LatLng(plan.lat, plan.lon)).zoom(plan.zoom).build()
                                }
                            }
                            lastFittedIds.value = schools.map { it.id }.toSet()
                            syncMarkers(
                                context, map, schools, scoresById,
                                showLabels = labelsVisible.value,
                                fitBounds = doFit,
                                userLat = userLat, userLon = userLon
                            ) { tappedSchool -> selectedSchool = tappedSchool }
                        }
                        map.uiSettings.apply {
                            isRotateGesturesEnabled = true
                            isTiltGesturesEnabled   = false
                            // Brújula debajo del botón de ampliar (izquierda),
                            // no en su posición por defecto (arriba derecha,
                            // donde ahora está el botón de capas) — mismo
                            // tratamiento que el mapa de detalle de escuela
                            // (SchoolMapView.kt). A pantalla completa el botón
                            // baja más para esquivar la barra de estado, así
                            // que la brújula baja con él (Álvaro, 2026-09-01).
                            isCompassEnabled = true
                            setCompassFadeFacingNorth(false)
                            setCompassGravity(android.view.Gravity.TOP or android.view.Gravity.START)
                            val d = context.resources.displayMetrics.density
                            val topMarginDp = if (fullscreenMap) 100 else 56
                            setCompassMargins((12 * d).toInt(), (topMarginDp * d).toInt(), 0, 0)
                        }
                        // Etiquetas de nombre solo a partir de zoom 8.5 — más
                        // lejos se solaparían unas con otras.
                        map.addOnCameraIdleListener {
                            val shouldShow = map.cameraPosition.zoom >= 8.5
                            if (shouldShow != labelsVisible.value) labelsVisible.value = shouldShow
                            // M2: recordar la cámara para restaurarla si el mapa se recrea.
                            savedCamera.value = map.cameraPosition
                        }
                    }
                    onStart()
                    onResume()
                }
            }
        )

        // Ampliar / salir de pantalla completa — arriba a la izquierda, misma
        // posición y forma que en el detalle de escuela. A pantalla completa
        // se respeta la barra de estado (antes quedaba debajo del reloj y no
        // se podía pulsar — Álvaro, 2026-09-01).
        Box(
            modifier = Modifier.align(Alignment.TopStart)
                .let { if (fullscreenMap) it.statusBarsPadding() else it }
                .padding(Spacing.sm)
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                .clickable { fullscreenMap = !fullscreenMap },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (fullscreenMap) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                contentDescription = stringResource(R.string.a11y_fullscreen_map),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }

        // Topo/satélite de un toque — mismo botón que el detalle de escuela.
        Box(
            modifier = Modifier.align(Alignment.TopEnd)
                .let { if (fullscreenMap) it.statusBarsPadding() else it }
                .padding(Spacing.sm)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                com.meteomontana.android.ui.components.SideMapButton(
                    active = true,
                    onClick = { isSatellite = !isSatellite }
                ) {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = stringResource(R.string.map_topo) + "/" + stringResource(R.string.map_satellite),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Botón de ubicación EN EL MAPA, igual que en iOS (Álvaro,
                // 2026-10-01): con ubicación → icono relleno terracota; sin
                // ella → pide el permiso o lleva a Ajustes si está denegado.
                com.meteomontana.android.ui.components.SideMapButton(
                    active = true,
                    onClick = onRequestLocation
                ) {
                    Icon(
                        if (locationPermission) Icons.Outlined.MyLocation else Icons.Outlined.LocationDisabled,
                        contentDescription = stringResource(R.string.explore_location_button),
                        tint = if (locationPermission) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // A pantalla completa hay demasiadas escuelas para verlas bien sin
        // filtrar — DISTANCIA y ESTILO aquí también, para no tener que salir
        // del mapa. Barra fija ABAJO (no un panel lateral que tapaba el mapa)
        // — Álvaro, 2026-09-01: "abajo me gusta más".
        if (fullscreenMap) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(stringResource(R.string.schools_map_panel_v2_dist), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DISTANCE_OPTIONS.forEach { km ->
                        MapFilterPill(
                            label = km?.let { "${it.toInt()} km" } ?: stringResource(R.string.schools_filter_all),
                            selected = km == distanceKm,
                            onClick = { onDistanceChange(km) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.schools_map_panel_v2_estilo), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StyleFilter.entries.forEach { s ->
                        MapFilterPill(
                            label = stringResource(s.labelRes),
                            selected = s == style,
                            onClick = { onStyleChange(s) }
                        )
                    }
                }
            }
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
                    .padding(horizontal = Spacing.md)
                    .padding(bottom = if (fullscreenMap) 92.dp else Spacing.md)
            )
        }
    }
    }

    if (fullscreenMap) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreenMap = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            mapBox(Modifier.fillMaxSize())
        }
    } else {
        mapBox(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(top = Spacing.xs)
        )
    }
}

@Composable
internal fun MapFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(com.meteomontana.android.ui.theme.CumbrePillShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                1.dp,
                if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                com.meteomontana.android.ui.theme.CumbrePillShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 8.dp)
    ) {
        Text(
            label, style = EyebrowTextStyle,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Markers                                                                    */
/* ─────────────────────────────────────────────────────────────────────────── */

internal val activeMarkers = mutableListOf<Marker>()
internal val markerSchoolBySnippet = mutableMapOf<String, String>()

/** Caché de iconos (ver comentario en syncMarkers: evita corromper el atlas). */
internal val panelIconCache = HashMap<String, org.maplibre.android.annotations.Icon>()
internal fun cachedPanelIcon(
    ctx: android.content.Context,
    key: String,
    make: () -> Bitmap
): org.maplibre.android.annotations.Icon =
    panelIconCache.getOrPut(key) { IconFactory.getInstance(ctx).fromBitmap(make()) }

/** Distancia aproximada en km. Null si no tenemos ubicación del usuario. */
internal fun haversineKm(lat1: Double?, lon1: Double?, lat2: Double, lon2: Double): Double? {
    if (lat1 == null || lon1 == null) return null
    return Geo.haversineKm(lat1, lon1, lat2, lon2)
}
