package com.meteomontana.android.ui.screens.approach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.data.api.dto.AddApproachPinRequest
import com.meteomontana.android.data.map.MapStyles
import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.domain.model.ApproachPin
import com.meteomontana.android.ui.components.MapViewLifecycleEffect
import com.meteomontana.android.ui.components.FullScreenPhotoDialog
import com.meteomontana.android.ui.components.parseWallPath
import com.meteomontana.android.ui.components.pinBitmap
import com.meteomontana.android.ui.components.rememberUserLocation
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.Warn
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Pantalla "SEGUIR" de una aproximación a pantalla completa — Fase 1/2 de
 * APPROACH_DESIGN.md §6.3/§6.4. Espejo de ApproachFollowView.swift (iOS).
 *
 * Sin navegación giro a giro: solo la línea y el punto azul del usuario.
 * "+ CHINCHETA" y borrar el camino son SOLO ADMIN por ahora (§2.6/§10).
 *
 * Nota de implementación: MapLibre annotations no soporta un trazo con guiones
 * de verdad (eso exige LineLayer de estilo), así que "sin verificar" se marca
 * con color ámbar + menor opacidad en vez de discontinuo — mismo mensaje
 * visual (aviso), API disponible más sencilla.
 */
@Composable
fun ApproachFollowScreen(
    approach: Approach,
    schoolName: String,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onDeleteApproach: (Approach) -> Unit,
    onAddPin: (String, AddApproachPinRequest) -> Unit
) {
    val userLoc = rememberUserLocation()
    var selectedPin by remember { mutableStateOf<ApproachPin?>(null) }
    var fullPhoto by remember { mutableStateOf<String?>(null) }
    var placingPin by remember { mutableStateOf(false) }
    var newPinCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pathCoords = remember(approach.pathJson) { parseWallPath(approach.pathJson) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Box(Modifier.fillMaxSize()) {
            val mapViewRef = remember { mutableStateOf<MapView?>(null) }
            MapViewLifecycleEffect(mapViewRef)

            androidx.compose.runtime.key(approach.id, placingPin) {
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
                                    val iconFactory = IconFactory.getInstance(context)
                                    val markerToPin = mutableMapOf<Marker, ApproachPin>()

                                    if (pathCoords.isNotEmpty()) {
                                        map.addPolyline(
                                            PolylineOptions()
                                                .addAll(pathCoords)
                                                .color(if (approach.isVerified)
                                                    android.graphics.Color.parseColor("#C2410C")
                                                else android.graphics.Color.parseColor("#B45309"))
                                                .alpha(if (approach.isVerified) 1f else 0.75f)
                                                .width(4f)
                                        )
                                        val boundsBuilder = LatLngBounds.Builder()
                                        pathCoords.forEach { boundsBuilder.include(it) }
                                        runCatching {
                                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
                                        }
                                    } else {
                                        map.cameraPosition = CameraPosition.Builder()
                                            .target(LatLng(userLoc?.lat ?: 0.0, userLoc?.lon ?: 0.0))
                                            .zoom(15.0).build()
                                    }

                                    approach.pins.forEach { pin ->
                                        val bmp = pinBitmap(colorForPinKind(pin.kind), letterForPinKind(pin.kind), sizeDp = 30)
                                        val marker = map.addMarker(
                                            MarkerOptions()
                                                .position(LatLng(pin.lat, pin.lon))
                                                .title(pin.message ?: pin.kind)
                                                .icon(iconFactory.fromBitmap(bmp))
                                        )
                                        markerToPin[marker] = pin
                                    }

                                    userLoc?.let {
                                        map.addMarker(
                                            MarkerOptions()
                                                .position(LatLng(it.lat, it.lon))
                                                .icon(iconFactory.fromBitmap(
                                                    com.meteomontana.android.ui.components.userDotBitmap()))
                                        )
                                    }

                                    map.setOnMarkerClickListener { marker ->
                                        val pin = markerToPin[marker]
                                        if (pin != null) { selectedPin = pin; true } else false
                                    }
                                    map.addOnMapClickListener { point ->
                                        if (placingPin) {
                                            newPinCoord = point.latitude to point.longitude
                                            true
                                        } else false
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

            // Volver
            Box(
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(Spacing.md)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text("←", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }

            // Admin: borrar + añadir chincheta
            if (isAdmin) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { confirmDelete = true },
                        contentAlignment = Alignment.Center
                    ) { Text("🗑") }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (placingPin) MaterialTheme.colorScheme.onSurfaceVariant else Terra)
                            .clickable { placingPin = !placingPin }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (placingPin) "TOCA EL MAPA" else "+ CHINCHETA",
                            style = EyebrowTextStyle, color = Color.White)
                    }
                }
            }

            // Cabecera con nombre + verificación
            Row(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 60.dp, start = Spacing.xl, end = Spacing.xl)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f), RoundedCornerShape(2.dp))
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(approach.name ?: "$schoolName: aproximación",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                Text(if (approach.isVerified) "✓ VERIFICADA" else "⚠ SIN VERIFICAR",
                    style = EyebrowTextStyle,
                    color = if (approach.isVerified) Ok else Warn)
            }

            // Banner inferior
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    .padding(Spacing.md)
            ) {
                Text(
                    if (placingPin) "Toca el mapa donde quieras dejar la chincheta."
                    else "Sigue la línea. Si te alejas del camino, comprueba las chinchetas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Ficha de una chincheta
    selectedPin?.let { pin ->
        AlertDialog(
            onDismissRequest = { selectedPin = null },
            title = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(pin.kind, style = EyebrowTextStyle, color = Terra)
                    if (pin.status != "VERIFIED") {
                        Text("SIN VERIFICAR", style = EyebrowTextStyle, color = Warn)
                    }
                }
            },
            text = {
                Column {
                    pin.photoPath?.takeIf { it.isNotBlank() }?.let { url ->
                        AsyncImage(
                            model = url, contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .clickable { fullPhoto = url }
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    pin.message?.takeIf { it.isNotBlank() }?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPin = null }) { Text("CERRAR") }
            }
        )
    }

    fullPhoto?.let { url ->
        FullScreenPhotoDialog(photoUrl = url, onDismiss = { fullPhoto = null })
    }

    // Alta de chincheta (admin, mientras se sigue una aproximación ya publicada).
    newPinCoord?.let { (lat, lon) ->
        NewApproachPinDialog(
            onDismiss = { newPinCoord = null },
            onSave = { req ->
                onAddPin(approach.id, req.copy(lat = lat, lon = lon))
                placingPin = false
                newPinCoord = null
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar «${approach.name ?: "esta aproximación"}»?") },
            text = { Text("Se borra el camino y todas sus chinchetas. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteApproach(approach)
                    onDismiss()
                }) { Text("BORRAR", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCELAR") } }
        )
    }
}

internal fun colorForPinKind(kind: String): Int = when (kind) {
    "FORK" -> android.graphics.Color.parseColor("#C2410C")
    "HAZARD" -> android.graphics.Color.parseColor("#B45309")
    "KEY" -> android.graphics.Color.parseColor("#8E3FBF")
    else -> android.graphics.Color.parseColor("#5A574F") // LANDMARK
}

internal fun letterForPinKind(kind: String): String = when (kind) {
    "FORK" -> "◆"
    "HAZARD" -> "▲"
    "KEY" -> "★"
    else -> "●" // LANDMARK
}
