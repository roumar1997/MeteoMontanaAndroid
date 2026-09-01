package com.meteomontana.android.ui.screens.schools

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
    onToggle: () -> Unit,
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
    onStyleChange: (StyleFilter) -> Unit
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {

        // Toggle "VER MAPA" — botón terracota (borde + texto + tinte) para que se
        // vea claramente pulsable (antes era una barra gris que parecía pasiva).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(com.meteomontana.android.ui.theme.CumbrePillShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .border(1.dp, MaterialTheme.colorScheme.primary, com.meteomontana.android.ui.theme.CumbrePillShape)
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
                if (expanded) stringResource(R.string.schools_hide_map) else stringResource(R.string.schools_view_map),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            // Chevron de verdad, no un triángulo de texto: es lo que dibuja
            // iOS (`chevron.down`). El glifo ▾ se veía macizo y más pequeño,
            // y además depende de la fuente que tenga el móvil.
            androidx.compose.material3.Icon(
                imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp
                              else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (expanded) {
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
                onStyleChange = onStyleChange
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
    onSchoolDetail: (String) -> Unit,
    mapState: SchoolsMapState,
    distanceKm: Double?,
    onDistanceChange: (Double?) -> Unit,
    style: StyleFilter,
    onStyleChange: (StyleFilter) -> Unit
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
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text("DIST.", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("ESTILO", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StyleFilter.entries.forEach { s ->
                        MapFilterPill(
                            label = s.label,
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
private fun MapFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = Spacing.sm, vertical = 4.dp)
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

private val activeMarkers = mutableListOf<Marker>()
private val markerSchoolBySnippet = mutableMapOf<String, String>()

/** Caché de iconos (ver comentario en syncMarkers: evita corromper el atlas). */
private val panelIconCache = HashMap<String, org.maplibre.android.annotations.Icon>()
private fun cachedPanelIcon(
    ctx: android.content.Context,
    key: String,
    make: () -> Bitmap
): org.maplibre.android.annotations.Icon =
    panelIconCache.getOrPut(key) { IconFactory.getInstance(ctx).fromBitmap(make()) }

/**
 * Borra los markers anteriores y crea uno por escuela visible. Hace fit-bounds
 * para que la cámara encuadre lo que el usuario está filtrando.
 */
internal fun syncMarkers(
    ctx: android.content.Context,
    map: MapLibreMap,
    schools: List<School>,
    scoresById: Map<String, Int>,
    showLabels: Boolean,
    fitBounds: Boolean,
    userLat: Double?,
    userLon: Double?,
    tiny: Boolean = false,   // zoom lejano: puntito en vez de diamante
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
                .icon(cachedPanelIcon(ctx, "user") {
                    com.meteomontana.android.ui.components.userDotBitmap() })
        )
    }

    if (schools.isEmpty()) return

    val boundsBuilder = LatLngBounds.Builder()
    schools.forEach { s ->
        val score = scoresById[s.id]
        // Escalar por densidad: en px fijos el pin salía diminuto en pantallas
        // de alta densidad (los móviles modernos, o sea, todos).
        val density = ctx.resources.displayMetrics.density
        // Icono CACHEADO por clave: sin caché, cada re-sync registra sprites
        // nuevos en el atlas de MapLibre y acaba corrompiéndolo (markers
        // pintados como bandas gigantes al hacer zoom).
        val label = if (showLabels) s.name else null
        val key = if (tiny) "dot:$score" else "dia:$score:${label ?: ""}"
        val icon = cachedPanelIcon(ctx, key) {
            if (tiny) dotBitmap(score, density)
            else diamondBitmap(score, label, density)
        }
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(s.lat, s.lon))
                .icon(icon)
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
        if (schools.size == 1) {
            // Una sola escuela (p.ej. buscador): encuadrar "bounds" de un único
            // punto hace que MapLibre se vaya al mundo entero. Zoom directo a ella
            // (como iOS), en vez de bounds degenerados.
            val s = schools.first()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(s.lat, s.lon), 13.5), 400)
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 48), 400)
        }
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
/** Puntito de color por score: para zoom país, donde 191 diamantes taparían todo. */
internal fun dotBitmap(score: Int?, density: Float = 2f): Bitmap {
    val k = density / 2f
    val size = (22 * k).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val c = size / 2f
    canvas.drawCircle(c, c, 8f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColorHex(score).toColorInt()
    })
    canvas.drawCircle(c, c, 8f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * k
        color = AndroidColor.WHITE
    })
    return bmp
}

private fun diamondBitmap(score: Int?, name: String?, density: Float = 2f): Bitmap {
    // Todo se dibuja a escala k para que el pin mida lo mismo (en dp) en
    // cualquier pantalla — igual de grande que en iPhone.
    val k = density / 2f
    val pinPx = (64 * k).toInt()
    val label = when {
        name == null -> null
        name.length > 16 -> name.take(15).trimEnd() + "…"
        else -> name
    }

    // Paints del nombre: halo blanco grueso debajo + texto tinta encima.
    val nameHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * k
        color = AndroidColor.WHITE
        textSize = 22f * k
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val nameInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#1A1A1A".toColorInt()
        textSize = 22f * k
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val nameWidth = if (label != null) nameHalo.measureText(label) + 12f * k else 0f
    val widthPx = maxOf(pinPx, nameWidth.toInt())
    val nameHeightPx = if (label != null) (28 * k).toInt() else 0
    val bmp = Bitmap.createBitmap(widthPx, pinPx + nameHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val color = pinColorHex(score).toColorInt()

    // Sombra suave bajo el diamante
    val cx = widthPx / 2f
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.argb(60, 0, 0, 0)
    }
    canvas.drawOval(RectF(cx - 22f * k, pinPx - 16f * k, cx + 22f * k, pinPx - 6f * k), shadow)

    // Diamante: cuadrado rotado 45º, esquina inferior es la "punta"
    val side = pinPx * 0.55f
    val cy = pinPx / 2f - 4f * k
    canvas.save()
    canvas.rotate(45f, cx, cy)
    val rect = RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawRoundRect(rect, 6f * k, 6f * k, fill)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * k
        this.color = AndroidColor.WHITE
    }
    canvas.drawRoundRect(rect, 6f * k, 6f * k, stroke)
    canvas.restore()

    // Score blanco centrado
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        textSize = 18f * k
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(score?.toString() ?: "·", cx, cy + 6f * k, txt)

    // Nombre debajo del pin (halo primero, tinta encima)
    if (label != null) {
        val nameY = pinPx + nameHeightPx - 8f * k
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

    // Mini-ficha (mismo estilo que la de parkings/sectores del mapa de escuela).
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline,
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Chip de score: el mismo color del pin, con el número dentro.
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(androidx.compose.ui.graphics.Color(scoreColor.toColorInt())),
                contentAlignment = Alignment.Center
            ) {
                Text(score?.toString() ?: "·",
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.ui.graphics.Color.White)
            }
            Text(
                school.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                "✕",
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
                text = stringResource(R.string.common_directions),
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
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline,
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
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
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            color = androidx.compose.ui.graphics.Color.White)
    }
}

/* ─────────────────────────────────────────────────────────────────────────── */

/** Estilos MapLibre raster (fuente única en MapStyles). Mismo origen que la PWA. */
internal val OSM_RASTER_STYLE get() = MapStyles.osmPaper
internal val DARK_RASTER_STYLE get() = MapStyles.darkPaper
internal val SATELLITE_RASTER_STYLE get() =
    MapStyles.raster("esri", MapStyles.SATELLITE, "© Esri", MapStyles.PAPER_BG)

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
