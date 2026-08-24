package com.meteomontana.android.ui.screens.approach

import com.meteomontana.android.ui.theme.terraFillColor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.data.api.dto.AddApproachPinRequest
import com.meteomontana.android.data.api.dto.CreateApproachRequest
import com.meteomontana.android.data.map.MapStyles
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.domain.util.Geo
import com.meteomontana.android.ui.components.MapViewLifecycleEffect
import com.meteomontana.android.ui.components.pinBitmap
import com.meteomontana.android.ui.components.rememberUserLocation
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale

/**
 * "GRABAR APROXIMACIÓN" — APPROACH_DESIGN.md §6.2/§6.4. SOLO ADMIN por ahora
 * (ver ApproachesSection). Espejo de ApproachRecordView.swift (iOS).
 *
 * DESVIACIÓN de alcance respecto al diseño/iOS (documentada en el informe de
 * la tarea): sin servicio en primer plano dedicado (§2.5) — la grabación usa
 * el mismo sondeo de ubicación cada 5s que ya usa el resto de la app
 * (`rememberUserLocation`), válido mientras la pantalla está abierta y la app
 * en primer plano. Las chinchetas se suben (foto incluida) en cuanto se
 * añaden en vez de esperar a tener el id del camino — simplifica el flujo sin
 * cambiar el resultado (el backend no exige el id de la aproximación para
 * subir la foto, solo para el POST de la chincheta en sí, que sigue yendo
 * al final).
 */
@Composable
fun ApproachRecordScreen(
    school: com.meteomontana.android.domain.model.School,
    blocks: List<Block>,
    onDismiss: () -> Unit,
    onSave: suspend (CreateApproachRequest, List<AddApproachPinRequest>) -> Result<Unit>
) {
    val parkings = remember(blocks) { blocks.filter { it.type == "PARKING" } }
    val sectors = remember(blocks) { blocks.filter { it.type == "ZONE" || it.type == "BLOCK" } }

    var fromBlockId by remember { mutableStateOf<String?>(null) }
    var toBlockId by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf<List<UserLocation>>(emptyList()) }
    var distanceM by remember { mutableStateOf(0.0) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var savingStep by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var addingPin by remember { mutableStateOf(false) }
    val pendingPins = remember { mutableStateOf<List<Triple<Double, Double, AddApproachPinRequest>>>(emptyList()) }

    val userLoc = rememberUserLocation()
    val scope = rememberCoroutineScope()

    // Cronómetro + muestreo del track: 1 punto/s mientras se graba y no está
    // en pausa (el diseño pide 1 pto/s o 5 m; aquí simplificamos a 1 pto/s
    // con la ubicación ya refrescada cada 5s por rememberUserLocation, que es
    // el mecanismo continuo existente en el resto de la app).
    LaunchedEffect(recording, paused) {
        if (!recording) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (!recording) break
            elapsedSeconds += 1
            if (!paused) {
                userLoc?.let { u ->
                    val last = points.lastOrNull()
                    if (last == null || Geo.haversineKm(last.lat, last.lon, u.lat, u.lon) * 1000 > 3) {
                        if (last != null) distanceM += Geo.haversineKm(last.lat, last.lon, u.lat, u.lon) * 1000
                        points = points + u
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (savingStep) "Guardar camino" else "Grabar aproximación",
                    style = MaterialTheme.typography.titleMedium)
                Text("CERRAR", style = EyebrowTextStyle, color = Terra,
                    modifier = Modifier.clickable(onClick = onDismiss))
            }

            if (!savingStep) {
                if (!recording) {
                    Column(Modifier.padding(horizontal = Spacing.md)) {
                        Text("ORIGEN (parking)", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        BlockPicker(parkings, fromBlockId, "Elige un parking") { fromBlockId = it }
                        Spacer(Modifier.height(Spacing.sm))
                        Text("DESTINO (sector/piedra)", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        BlockPicker(sectors, toBlockId, "Elige un sector") { toBlockId = it }
                        Spacer(Modifier.height(Spacing.sm))
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    RecordMapView(
                        school = school, blocks = blocks, userLoc = userLoc,
                        track = points, pendingPins = pendingPins.value.map { it.first to it.second },
                        placingPin = false,
                        onMapTap = { _, _ -> }
                    )
                    if (recording) {
                        // La chincheta se deja en TU posición actual (andando
                        // por el camino), no tocando el mapa — así no hay que
                        // dejar de mirar el terreno para acertar un punto.
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm)
                                .clip(RoundedCornerShape(2.dp))
                                .background(terraFillColor())
                                .clickable { addingPin = true }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        ) {
                            Text("+ CHINCHETA AQUÍ",
                                style = EyebrowTextStyle, color = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (pendingPins.value.isNotEmpty()) {
                        Text("${pendingPins.value.size} chincheta${if (pendingPins.value.size == 1) "" else "s"} en este camino",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(formatElapsed(elapsedSeconds), style = MaterialTheme.typography.headlineMedium)
                    Text(formatDistance(distanceM), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        if (recording) {
                            Box(
                                modifier = Modifier.weight(1f)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                                    .clickable { paused = !paused }
                                    .padding(vertical = Spacing.md),
                                contentAlignment = Alignment.Center
                            ) { Text(if (paused) "REANUDAR" else "PAUSAR", style = EyebrowTextStyle) }
                            Box(
                                modifier = Modifier.weight(1f)
                                    .background(terraFillColor(), RoundedCornerShape(2.dp))
                                    .clickable {
                                        recording = false
                                        addingPin = false
                                        savingStep = true
                                    }
                                    .padding(vertical = Spacing.md),
                                contentAlignment = Alignment.Center
                            ) { Text("TERMINAR", style = EyebrowTextStyle, color = Color.White) }
                        } else {
                            val canStart = fromBlockId != null && toBlockId != null
                            Box(
                                modifier = Modifier.weight(1f)
                                    .background(if (canStart) Terra else MaterialTheme.colorScheme.onSurfaceVariant,
                                        RoundedCornerShape(2.dp))
                                    .clickable(enabled = canStart) {
                                        recording = true; points = emptyList(); distanceM = 0.0; elapsedSeconds = 0
                                    }
                                    .padding(vertical = Spacing.lg),
                                contentAlignment = Alignment.Center
                            ) { Text("INICIAR", style = EyebrowTextStyle, color = Color.White) }
                        }
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().padding(Spacing.md)) {
                    Text("Nombre del camino", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        placeholder = { Text("p. ej. Parking alto → Sector Techos") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "${formatDistance(distanceM)} · ${formatElapsed(elapsedSeconds)} · ${points.size} puntos" +
                            if (pendingPins.value.isEmpty()) "" else " · ${pendingPins.value.size} chinchetas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    errorMsg?.let {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(terraFillColor(), RoundedCornerShape(2.dp))
                            .clickable(enabled = !saving) {
                                if (points.size < 2) {
                                    errorMsg = "El camino grabado es demasiado corto."
                                    return@clickable
                                }
                                scope.launch {
                                    saving = true
                                    errorMsg = null
                                    val pathJson = "[" + points.joinToString(",") { "[${it.lat},${it.lon}]" } + "]"
                                    val req = CreateApproachRequest(
                                        fromBlockId = fromBlockId, toBlockId = toBlockId,
                                        name = name.ifBlank { null },
                                        pathJson = pathJson,
                                        distanceM = distanceM.toInt(),
                                        ascentM = null,
                                        durationMin = elapsedSeconds / 60,
                                        source = "RECORDED"
                                    )
                                    val pinsReq = pendingPins.value.mapIndexed { idx, (lat, lon, base) ->
                                        base.copy(lat = lat, lon = lon, positionIdx = idx)
                                    }
                                    val result = onSave(req, pinsReq)
                                    saving = false
                                    if (result.isSuccess) onDismiss()
                                    else errorMsg = "No se pudo guardar. Inténtalo de nuevo."
                                }
                            }
                            .padding(vertical = Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (saving) CircularProgressIndicator(
                            modifier = Modifier.height(20.dp), color = Color.White)
                        else Text("GUARDAR", style = EyebrowTextStyle, color = Color.White)
                    }
                }
            }
        }
    }

    if (addingPin && recording) {
        // Se reutiliza el mismo diálogo que "seguir"; aquí la posición se fija
        // en la última ubicación conocida del usuario (grabando andando, el
        // usuario está en el punto donde quiere la chincheta).
        val u = userLoc
        if (u != null) {
            NewApproachPinDialog(
                onDismiss = { addingPin = false },
                onSave = { req ->
                    pendingPins.value = pendingPins.value + Triple(u.lat, u.lon, req)
                    addingPin = false
                }
            )
        }
    }
}

@Composable
private fun BlockPicker(
    options: List<Block>, selectedId: String?, placeholder: String, onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.id == selectedId }?.name ?: placeholder
    Box {
        Row(
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .clickable { expanded = true }
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(selectedName, color = if (selectedId == null)
                MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { b ->
                DropdownMenuItem(text = { Text(b.name.ifBlank { placeholder }) }, onClick = {
                    onSelect(b.id); expanded = false
                })
            }
        }
    }
}

@Composable
private fun RecordMapView(
    school: com.meteomontana.android.domain.model.School,
    blocks: List<Block>,
    userLoc: UserLocation?,
    track: List<UserLocation>,
    pendingPins: List<Pair<Double, Double>>,
    placingPin: Boolean,
    onMapTap: (Double, Double) -> Unit
) {
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    MapViewLifecycleEffect(mapViewRef)

    androidx.compose.runtime.key(track.size, pendingPins.size) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context, org.maplibre.android.maps.MapLibreMapOptions
                    .createFromAttributes(context).textureMode(true)).apply {
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
                        map.setStyle(Style.Builder().fromJson(MapStyles.satellite)) {
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(userLoc?.lat ?: school.lat, userLoc?.lon ?: school.lon))
                                .zoom(15.0).build()

                            val iconFactory = IconFactory.getInstance(context)
                            blocks.forEach { b ->
                                val letter = when (b.type) { "PARKING" -> "P"; "ZONE" -> "Z"; else -> null }
                                if (letter != null) {
                                    val color = when (b.type) {
                                        "PARKING" -> android.graphics.Color.parseColor("#1D6DD6")
                                        else -> android.graphics.Color.parseColor("#1FA84E")
                                    }
                                    map.addMarker(
                                        MarkerOptions().position(LatLng(b.lat, b.lon)).title(b.name)
                                            .icon(iconFactory.fromBitmap(pinBitmap(color, letter, sizeDp = 26)))
                                    )
                                }
                            }
                            if (track.size >= 2) {
                                map.addPolyline(
                                    PolylineOptions()
                                        .addAll(track.map { LatLng(it.lat, it.lon) })
                                        .color(android.graphics.Color.parseColor("#C2410C"))
                                        .width(4f)
                                )
                            }
                            pendingPins.forEach { (lat, lon) ->
                                map.addMarker(
                                    MarkerOptions().position(LatLng(lat, lon))
                                        .icon(iconFactory.fromBitmap(pinBitmap(
                                            android.graphics.Color.parseColor("#C2410C"), "●", sizeDp = 24)))
                                )
                            }
                            userLoc?.let {
                                map.addMarker(
                                    MarkerOptions().position(LatLng(it.lat, it.lon))
                                        .icon(iconFactory.fromBitmap(
                                            com.meteomontana.android.ui.components.userDotBitmap()))
                                )
                            }
                            map.addOnMapClickListener { point ->
                                if (placingPin) { onMapTap(point.latitude, point.longitude); true } else false
                            }
                        }
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isTiltGesturesEnabled = false
                    }
                    onStart(); onResume()
                }
            }
        )
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60; val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

private fun formatDistance(m: Double): String =
    if (m >= 1000) String.format(Locale.US, "%.2f km", m / 1000) else "${m.toInt()} m"
