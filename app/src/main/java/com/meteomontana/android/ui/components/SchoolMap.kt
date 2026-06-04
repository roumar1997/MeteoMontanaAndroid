package com.meteomontana.android.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meteomontana.android.data.api.dto.BlockDto
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions

/**
 * Mapa nativo MapLibre centrado en la escuela.
 * Renderiza los bloques/parkings/zonas como marcadores.
 */
@Composable
fun SchoolMap(
    centerLat: Double,
    centerLon: Double,
    blocks: List<BlockDto>,
    modifier: Modifier = Modifier
) {
    // Iniciar MapLibre con tile estilo demo (libre)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { MapLibre.getInstance(ctx) }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(280.dp),
        factory = { context ->
            MapView(context).apply {
                onCreate(null)
                getMapAsync { map ->
                    // Estilo público gratuito de MapLibre demo
                    map.setStyle(Style.Builder()
                        .fromUri("https://demotiles.maplibre.org/style.json")) { _ ->
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(centerLat, centerLon))
                            .zoom(15.0)
                            .build()

                        // Pinta marcadores: bloques en terra, parking en moss, zona en warn
                        blocks.forEach { b ->
                            map.addMarker(
                                MarkerOptions()
                                    .position(LatLng(b.lat, b.lon))
                                    .title(b.name)
                                    .snippet(b.type)
                            )
                        }
                    }
                    map.uiSettings.apply {
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                    }
                }
                onStart()
                onResume()
            }
        }
    )
}
