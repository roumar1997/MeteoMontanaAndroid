package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Mapa interactivo a pantalla completa con un marcador en [lat]/[lon].
 * Mismo motor (MapLibre + OSM) que el resto de la app.
 * Se cierra con el botón "✕ CERRAR" o pulsando atrás.
 *
 * Uso:
 *   if (showMap) FullScreenMapDialog(lat, lon, title) { showMap = false }
 */
@Composable
fun FullScreenMapDialog(
    lat: Double,
    lon: Double,
    markerTitle: String = "",
    onDismiss: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewRef.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START   -> mv.onStart()
                Lifecycle.Event.ON_RESUME  -> mv.onResume()
                Lifecycle.Event.ON_PAUSE   -> mv.onPause()
                Lifecycle.Event.ON_STOP    -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.apply { onPause(); onStop(); onDestroy() }
            mapViewRef.value = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Mapa MapLibre ocupando toda la pantalla
            AndroidView(
                modifier = Modifier.fillMaxSize(),
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
                            map.setStyle(Style.Builder().fromJson(OSM_TOPO_STYLE)) {
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(lat, lon))
                                    .zoom(15.0)
                                    .build()
                                map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(lat, lon))
                                        .title(markerTitle.ifBlank { "%.5f, %.5f".format(lat, lon) })
                                )
                            }
                            map.uiSettings.isRotateGesturesEnabled = false
                            map.uiSettings.isTiltGesturesEnabled   = false
                        }
                        onStart(); onResume()
                    }
                }
            )

            // Botón "✕ CERRAR" sobre el mapa (esquina superior derecha)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.md)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text("✕ CERRAR", style = EyebrowTextStyle, color = Color.White)
            }

            // Coordenadas en esquina inferior izquierda
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.md)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            ) {
                Text(
                    "%.6f, %.6f".format(lat, lon),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }
}

// Estilo topográfico (igual que el selector "Topográfico" en SchoolMap)
private val OSM_TOPO_STYLE = """
{"version":8,"sources":{"topo":{"type":"raster","tiles":[
  "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
  "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
  "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
],"tileSize":256,"attribution":"© OpenTopoMap (CC-BY-SA)"}},"layers":[{"id":"topo","type":"raster","source":"topo"}]}
""".trimIndent()
