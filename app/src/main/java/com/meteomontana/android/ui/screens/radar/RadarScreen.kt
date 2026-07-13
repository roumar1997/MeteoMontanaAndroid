package com.meteomontana.android.ui.screens.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.components.MapViewLifecycleEffect
import com.meteomontana.android.ui.components.rememberUserLocation
import com.meteomontana.android.ui.screens.schools.DARK_RASTER_STYLE
import com.meteomontana.android.ui.screens.schools.OSM_RASTER_STYLE
import com.meteomontana.android.ui.screens.schools.SATELLITE_RASTER_STYLE
import com.meteomontana.android.ui.screens.schools.syncMarkers
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.ImageSource

/**
 * Pestaña Radar v2 — "el mapa es la pantalla":
 * mapa a pantalla completa con la lluvia de AEMET (compuesto España cocinado
 * por el backend en azules Cumbre, alcance completo: mar/Portugal/Francia),
 * player flotante con hora grande y chips HOY/AYER, botonera lateral de
 * iconos (capas, escuelas, opacidad, ubicación). Autoplay al abrir.
 */
private const val RADAR_SOURCE = "radar-source"
private const val RADAR_LAYER = "radar-layer"

@Composable
fun RadarScreen(
    onSchoolDetail: (String) -> Unit = {},
    viewModel: RadarViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsState()
    val userLoc = rememberUserLocation()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }   // autoplay al abrir
    var opacity by remember { mutableStateOf(1f) }     // 100% por defecto
    var layersPanel by remember { mutableStateOf(false) }
    var selectedSchool by remember { mutableStateOf<School?>(null) }
    var showSchools by remember { mutableStateOf(true) }
    var isSatellite by remember { mutableStateOf(true) }   // satélite por defecto
    val labelsVisible = remember { mutableStateOf(false) }
    // A escala país 191 pines taparían España entera: solo con zoom cercano.
    val pinsVisible = remember { mutableStateOf(false) }

    MapViewLifecycleEffect(mapViewRef) { mapRef.value = null }

    LaunchedEffect(state.day) { viewModel.load(state.day) }

    val readyFrames = state.frames.filter { it.bitmap != null }

    // Cambio topo/satélite: setStyle borra fuentes y markers → el tick
    // re-dispara los efectos que pintan la lluvia y los pines.
    var styleTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(isSatellite) {
        val map = mapRef.value ?: return@LaunchedEffect
        val pos = map.cameraPosition
        val styleJson = when {
            isSatellite -> SATELLITE_RASTER_STYLE
            isDarkTheme -> DARK_RASTER_STYLE
            else -> OSM_RASTER_STYLE
        }
        map.setStyle(Style.Builder().fromJson(styleJson)) {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
            styleTick++
        }
    }

    // Animación: avanza mientras `playing`; al llegar al final se queda en AHORA.
    // OJO saltos: los PNGs se descargan de MÁS RECIENTE a más antiguo → la
    // lista de frames listos crece por DELANTE mientras descarga. Animar sobre
    // un ÍNDICE de esa lista mutante hacía que la hora saltara (16, 15, 17…):
    // el frame de un índice cambiaba de identidad con cada llegada, y además
    // el efecto (con size en la key) se reiniciaba a mitad de reproducción.
    // Fix: NO animar hasta que la secuencia esté completa (framesLoading=false,
    // lista ya estable) — mientras tanto se enseña fijo el frame más reciente.
    LaunchedEffect(playing, state.framesLoading) {
        if (!playing || state.framesLoading || readyFrames.size < 2) return@LaunchedEffect
        if (frameIndex >= readyFrames.size - 1) frameIndex = 0
        while (playing && frameIndex < readyFrames.size - 1) {
            delay(420)
            frameIndex++
        }
        playing = false
    }
    // Mientras descargan (o sin animación en marcha) → enseñar SIEMPRE el más
    // reciente (índice size-1): su identidad es estable aunque la lista crezca
    // por delante, así la hora mostrada no baila.
    LaunchedEffect(readyFrames.size, state.framesLoading) {
        if ((state.framesLoading || !playing) && readyFrames.isNotEmpty()) {
            frameIndex = readyFrames.size - 1
        }
    }

    // Capa del radar: se CREA una vez (por estilo) y luego los cambios de
    // frame/opacidad actualizan la fuente directamente — así arrastrar la
    // barra es fluido, sin reconstruir nada.
    val radarSourceRef = remember { mutableStateOf<ImageSource?>(null) }
    LaunchedEffect(mapRef.value, state.bounds, styleTick, readyFrames.isNotEmpty()) {
        val map = mapRef.value ?: return@LaunchedEffect
        val b = state.bounds ?: return@LaunchedEffect
        val bmp = readyFrames.firstOrNull()?.bitmap ?: return@LaunchedEffect
        map.getStyle { style ->
            val quad = LatLngQuad(
                LatLng(b.north, b.west), LatLng(b.north, b.east),
                LatLng(b.south, b.east), LatLng(b.south, b.west))
            val existing = style.getSource(RADAR_SOURCE) as? ImageSource
            if (existing == null) {
                val src = ImageSource(RADAR_SOURCE, quad, bmp)
                style.addSource(src)
                style.addLayer(RasterLayer(RADAR_LAYER, RADAR_SOURCE).withProperties(
                    PropertyFactory.rasterOpacity(opacity),
                    // Suavizado al escalar: la resolución del radar es ~2km/px.
                    PropertyFactory.rasterResampling("linear")))
                radarSourceRef.value = src
            } else {
                radarSourceRef.value = existing
            }
        }
    }
    // Actualización barata del frame visible.
    LaunchedEffect(frameIndex, readyFrames.size, radarSourceRef.value) {
        val bmp = readyFrames.getOrNull(
            frameIndex.coerceIn(0, (readyFrames.size - 1).coerceAtLeast(0)))?.bitmap
            ?: return@LaunchedEffect
        radarSourceRef.value?.setImage(bmp)
    }
    // Opacidad, aparte: solo cuando se mueve su deslizador.
    LaunchedEffect(opacity, radarSourceRef.value) {
        mapRef.value?.getStyle { style ->
            (style.getLayer(RADAR_LAYER) as? RasterLayer)
                ?.setProperties(PropertyFactory.rasterOpacity(opacity))
        }
    }

    // Pines de escuelas (mismos markers que el mapa de Escuelas, con score).
    LaunchedEffect(state.schools, state.scoresById, mapRef.value, labelsVisible.value,
        showSchools, pinsVisible.value, styleTick) {
        val map = mapRef.value ?: return@LaunchedEffect
        if (state.schools.isEmpty()) return@LaunchedEffect
        // De lejos: puntitos de color (no tapan). De cerca: diamante con score.
        syncMarkers(
            ctx, map, if (showSchools) state.schools else emptyList(), state.scoresById,
            showLabels = labelsVisible.value,
            fitBounds = false,
            userLat = userLoc?.lat, userLon = userLoc?.lon,
            tiny = !pinsVisible.value
        ) { tapped -> selectedSchool = tapped }
    }

    val currentLabel = readyFrames.getOrNull(frameIndex)?.timeLabel ?: "--:--"
    val isNow = state.day == RadarDay.HOY &&
            readyFrames.isNotEmpty() && frameIndex == readyFrames.size - 1

    // ── El mapa ES la pantalla; todo lo demás flota encima.
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                // textureMode: MapLibre por defecto pinta en un SurfaceView, que
                // vive en una capa aparte del sistema — al cambiar de pestaña se
                // queda su último frame "fantasma" hasta que la nueva pantalla
                // termina de componer. Con TextureView el mapa se compone como
                // una vista normal y la transición es limpia.
                val opts = org.maplibre.android.maps.MapLibreMapOptions
                    .createFromAttributes(context).textureMode(true)
                MapView(context, opts).apply {
                    onCreate(null)
                    mapViewRef.value = this
                    getMapAsync { map ->
                        mapRef.value = map
                        val styleJson = SATELLITE_RASTER_STYLE   // satelite por defecto
                        map.setStyle(Style.Builder().fromJson(styleJson)) {
                            // España entera de inicio; el usuario decide el zoom.
                            map.cameraPosition =
                                org.maplibre.android.camera.CameraPosition.Builder()
                                    .target(LatLng(39.6, -3.6))
                                    .zoom(4.6)
                                    .build()
                        }
                        map.uiSettings.isTiltGesturesEnabled = false
                        map.addOnCameraIdleListener {
                            val zoom = map.cameraPosition.zoom
                            val pins = zoom >= 6.5
                            val labels = zoom >= 8.5
                            if (pins != pinsVisible.value) pinsVisible.value = pins
                            if (labels != labelsVisible.value) labelsVisible.value = labels
                        }
                    }
                    onStart(); onResume()
                }
            })

        // Título flotante. Baja 48dp: el conmutador TIEMPO⇄RADAR de la primera
        // pestaña flota arriba-centro (MainScreen) y en móviles estrechos
        // pisaba este rótulo — el título queda en una "fila" propia debajo.
        Column(
            modifier = Modifier.align(Alignment.TopStart)
                .statusBarsPadding().padding(top = 48.dp).padding(Spacing.sm)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("RADAR", style = EyebrowTextStyle, color = Terra)
            Text("Lluvia en directo",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif))
        }

        // Crédito AEMET (licencia)
        Text(
            "AEMET",
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd)
                .statusBarsPadding().padding(Spacing.sm)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp))

        // Botonera lateral (derecha): botones directos de un toque.
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SideButton(Icons.Outlined.Layers, "Topo o satélite",
                tint = if (isSatellite) Terra else MaterialTheme.colorScheme.onSurfaceVariant) {
                isSatellite = !isSatellite
            }
            SideButton(Icons.Outlined.Place, "Ver u ocultar escuelas",
                tint = if (showSchools) Terra else MaterialTheme.colorScheme.onSurfaceVariant) {
                showSchools = !showSchools
            }
            SideButton(Icons.Outlined.WaterDrop, "Intensidad de la lluvia",
                tint = Color(0xFF2B6DE3)) { layersPanel = !layersPanel }
            if (userLoc != null) {
                SideButton(Icons.Outlined.MyLocation, "Mi ubicación",
                    tint = Color(0xFF1A56DB)) {
                    mapRef.value?.let { m ->
                        runCatching {
                            m.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(userLoc.lat, userLoc.lon), 9.0))
                        }
                    }
                }
            }
        }

        // Panel pequeño de opacidad (se abre desde la gota).
        if (layersPanel) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .padding(end = 62.dp)
                    .width(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("LLUVIA ${(opacity * 100).toInt()}%", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = opacity, onValueChange = { opacity = it },
                    valueRange = 0.2f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Terra, activeTrackColor = Terra,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                            .copy(alpha = 0.4f)))
            }
        }

        // Leyenda (encima del player)
        Row(
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = Spacing.sm, bottom = 236.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LegendDot(Color(0xFF5C8FD6)); Text("DÉBIL", style = EyebrowTextStyle)
            Spacer(Modifier.width(3.dp))
            LegendDot(Color(0xFF3D6FBF)); Text("MEDIA", style = EyebrowTextStyle)
            Spacer(Modifier.width(3.dp))
            LegendDot(Color(0xFF274F98)); Text("FUERTE", style = EyebrowTextStyle)
        }

        // Mini-ficha de escuela (tap en pin) — sube por encima del player.
        selectedSchool?.let { school ->
            RadarSchoolCard(
                school = school,
                onDetail = { onSchoolDetail(school.id) },
                onClose = { selectedSchool = null },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(start = Spacing.sm, end = Spacing.sm, bottom = 236.dp))
        }

        if (state.error != null) {
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    .padding(Spacing.md))
        }

        // ── Player flotante: por ENCIMA de la cápsula de tabs, que flota
        // sobre el mapa (el mapa solo asoma por los lados de la cápsula).
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.sm)
                .padding(bottom = 78.dp, top = Spacing.sm)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayChip("HOY", state.day == RadarDay.HOY) {
                    playing = false; frameIndex = 0; viewModel.load(RadarDay.HOY)
                }
                Spacer(Modifier.width(5.dp))
                DayChip("AYER", state.day == RadarDay.AYER) {
                    playing = false; frameIndex = 0; viewModel.load(RadarDay.AYER)
                }
                Spacer(Modifier.weight(1f))
                // La hora del frame, en grande: siempre sabes qué estás viendo.
                Text(currentLabel,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = Serif, fontWeight = FontWeight.Bold))
                if (isNow) {
                    Spacer(Modifier.width(6.dp))
                    Text("AHORA", style = EyebrowTextStyle, color = Terra)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(Terra)
                        .clickable(enabled = readyFrames.size >= 2) { playing = !playing },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pausar" else "Reproducir",
                        tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Slider(
                        value = frameIndex.toFloat(),
                        onValueChange = {
                            playing = false
                            frameIndex = it.toInt()
                                .coerceIn(0, (readyFrames.size - 1).coerceAtLeast(0))
                        },
                        valueRange = 0f..(readyFrames.size - 1).coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Terra, activeTrackColor = Terra,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                                .copy(alpha = 0.4f)))
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(readyFrames.firstOrNull()?.timeLabel ?: "",
                            style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(readyFrames.lastOrNull()?.timeLabel ?: "",
                            style = EyebrowTextStyle,
                            color = if (isNow) Terra
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SideButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint,
            modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.onSurface
                else Color.Transparent)
            .border(1.dp,
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, style = EyebrowTextStyle,
            color = if (selected) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
}

/** Mini-ficha flotante al tocar una escuela. */
@Composable
private fun RadarSchoolCard(
    school: School,
    onDetail: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Column(Modifier.weight(1f)) {
            Text(school.name, style = MaterialTheme.typography.titleMedium)
            school.region?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Terra)
                .clickable(onClick = onDetail)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("VER DETALLE", style = EyebrowTextStyle, color = Color.White)
        }
        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
    }
}
