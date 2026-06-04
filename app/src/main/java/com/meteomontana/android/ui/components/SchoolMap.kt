package com.meteomontana.android.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meteomontana.android.data.api.dto.BlockDto
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Estilos de mapa.
 * - SATELLITE: ESRI World Imagery (libre)
 * - TOPO:      OpenTopoMap (CC-BY-SA, libre)
 */
private enum class MapStyleOption(val label: String) {
    SATELLITE("Satélite"),
    TOPO("Topográfico")
}

private fun styleJsonFor(style: MapStyleOption): String = when (style) {
    MapStyleOption.SATELLITE -> """
    {
      "version": 8,
      "sources": {
        "sat": {
          "type": "raster",
          "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
          "tileSize": 256,
          "attribution": "Tiles © Esri"
        }
      },
      "layers": [{ "id": "sat", "type": "raster", "source": "sat" }]
    }
    """.trimIndent()
    MapStyleOption.TOPO -> """
    {
      "version": 8,
      "sources": {
        "topo": {
          "type": "raster",
          "tiles": [
            "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
            "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
            "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
          ],
          "tileSize": 256,
          "attribution": "© OpenTopoMap (CC-BY-SA)"
        }
      },
      "layers": [{ "id": "topo", "type": "raster", "source": "topo" }]
    }
    """.trimIndent()
}

@Composable
fun SchoolMap(
    centerLat: Double,
    centerLon: Double,
    blocks: List<BlockDto>,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { MapLibre.getInstance(ctx) }

    var currentStyle by remember { mutableStateOf(MapStyleOption.SATELLITE) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapStyleOption.entries.forEach { option ->
                StyleChip(option.label, currentStyle == option, onClick = {
                    if (currentStyle != option) {
                        currentStyle = option
                        mapView?.getMapAsync { map ->
                            map.setStyle(Style.Builder().fromJson(styleJsonFor(option))) {
                                placeMarkers(map, blocks)
                            }
                        }
                    }
                })
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            factory = { context ->
                MapView(context).apply {
                    mapView = this
                    onCreate(null)
                    getMapAsync { map ->
                        map.setStyle(Style.Builder().fromJson(styleJsonFor(currentStyle))) {
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(centerLat, centerLon))
                                .zoom(15.0).build()
                            placeMarkers(map, blocks)
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
}

private fun placeMarkers(map: MapLibreMap, blocks: List<BlockDto>) {
    map.clear()
    blocks.forEach { b ->
        map.addMarker(
            MarkerOptions()
                .position(LatLng(b.lat, b.lon))
                .title(b.name)
                .snippet(b.type)
        )
    }
}

@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    val bg = if (selected) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.surface
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val border = if (selected) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}
