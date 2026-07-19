package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.screens.detail.SchoolDetailViewModel
import com.meteomontana.android.ui.screens.detail.ProposeContributionFlow
import com.meteomontana.android.ui.screens.detail.AddLinesFlow
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.meteomontana.android.domain.util.Geo

/**
 * SINCRONIZA los datos (piedras/parkings/zonas/usuario/previews) con las
 * anotaciones de MapLibre y resuelve marker->Block en los taps.
 *
 * CLASE con estado de INSTANCIA (antes: mapas mutables globales a nivel de
 * fichero — dos mapas vivos habrian colisionado). Cada mapa crea su placer.
 */
internal class MarkerPlacer {

    // Mapa de markers activos para poder mapear marker -> Block al tocar
    private val markerBlockMap = mutableMapOf<Long, Block>()
    private val polylineBlockMap = mutableMapOf<Long, Block>()

    fun place(
        ctx: android.content.Context,
        map: MapLibreMap,
        blocks: List<Block>,
        ghost: com.meteomontana.android.ui.screens.detail.CorrectionGhost? = null,
        userLoc: com.meteomontana.android.domain.model.UserLocation? = null,
        wallPreview: List<Pair<Double, Double>> = emptyList(),
        headingDeg: Float? = null,
        onBlockTap: (Block) -> Unit
    ) {
        map.clear()
        markerBlockMap.clear()
        polylineBlockMap.clear()
    
        val iconFactory = IconFactory.getInstance(ctx)
    
        // Punto azul con la posición del usuario (si la tenemos): así se ve
        // cómo de cerca estás de la escuela y sus sectores.
        if (userLoc != null) {
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(userLoc.lat, userLoc.lon))
                    .icon(cachedIcon(iconFactory, "user:${headingDeg ?: -1f}") { userDotBitmap(headingDeg) })
            )
        }
    
        // Orden de pintado: piedras primero, PARKING/ZONE/SCHOOL después → los
        // sectores no quedan tapados por las piedras.
        val paintOrder = blocks.sortedBy { b ->
            when (b.type.uppercase()) {
                "BLOCK" -> 0; "PARKING" -> 1; "ZONE" -> 2; else -> 3
            }
        }
        paintOrder.forEach { b ->
            // Si este marker está siendo movido (es el original), lo pintamos semitransparente.
            val isOriginalBeingMoved = ghost != null && (
                (ghost.originalId == null && b.id == "__SCHOOL__") ||
                (ghost.originalId != null && b.id == ghost.originalId)
            )
            // MURO (geometry=LINE) con polilínea válida: se pinta como LÍNEA del MISMO
            // color que la piedra (no como marcador) → 1 muro = 1 línea, no 16 pines.
            // El número va en un marcador en el punto medio (tappable, mismo estilo).
            val wallPath = if (b.type.equals("BLOCK", true) && b.geometry.equals("LINE", true))
                parseWallPath(b.path) else emptyList()
            if (wallPath.size >= 2) {
                val polyline = map.addPolyline(
                    PolylineOptions()
                        .addAll(wallPath)
                        .color(android.graphics.Color.parseColor(PIEDRA_COLOR))
                        .alpha(if (isOriginalBeingMoved) 0.35f else 1f)
                        .width(5f)
                )
                polylineBlockMap[polyline.id] = b
                val mid = wallPath[wallPath.size / 2]
                val midMarker = map.addMarker(
                    MarkerOptions()
                        .position(mid)
                        .icon(cachedIcon(iconFactory, "block:${b.name}:$isOriginalBeingMoved") {
                            blockBitmap(b.name).fadedIf(isOriginalBeingMoved)
                        })
                        .title(b.name)
                )
                markerBlockMap[midMarker.id] = b
                return@forEach
            }
    
            val icon = when (b.type.uppercase()) {
                "PARKING" -> cachedIcon(iconFactory, "parking:$isOriginalBeingMoved") {
                    parkingBitmap().fadedIf(isOriginalBeingMoved) }
                "ZONE"    -> cachedIcon(iconFactory, "zone:$isOriginalBeingMoved") {
                    zoneBitmap().fadedIf(isOriginalBeingMoved) }
                "SCHOOL"  -> cachedIcon(iconFactory, "school:${b.name}:$isOriginalBeingMoved") {
                    schoolBitmap(b.name).fadedIf(isOriginalBeingMoved) }
                else      -> cachedIcon(iconFactory, "block:${b.name}:$isOriginalBeingMoved") {
                    blockBitmap(b.name).fadedIf(isOriginalBeingMoved) }
            }
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(b.lat, b.lon))
                    .icon(icon)
                    .title(b.name)
            )
            markerBlockMap[marker.id] = b
        }
    
        // Preview del muro en construcción: polilínea terra + un punto numerado por tap.
        if (wallPreview.isNotEmpty()) {
            val pts = wallPreview.map { (lat, lon) -> LatLng(lat, lon) }
            if (pts.size >= 2) {
                map.addPolyline(
                    PolylineOptions()
                        .addAll(pts)
                        .color(android.graphics.Color.parseColor(PIEDRA_COLOR))
                        .width(5f)
                )
            }
            pts.forEachIndexed { idx, p ->
                map.addMarker(
                    MarkerOptions()
                        .position(p)
                        .icon(cachedIcon(iconFactory, "wallpt:${idx + 1}") { wallPointBitmap(idx + 1) })
                )
            }
        }
    
        // Ghost marker en la nueva posición candidata.
        if (ghost?.newLat != null && ghost.newLon != null) {
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(ghost.newLat, ghost.newLon))
                    .icon(cachedIcon(iconFactory, "ghost") { ghostBitmap() })
                    .title("Nueva posición")
            )
        }
    
        map.setOnMarkerClickListener { marker ->
            markerBlockMap[marker.id]?.let { onBlockTap(it) }
            true
        }
        // Tocar la LÍNEA de un muro abre su ficha (igual que tocar su número).
        map.setOnPolylineClickListener { polyline ->
            polylineBlockMap[polyline.id]?.let { onBlockTap(it) }
        }
    }
}

/** Parsea el `path` de un muro ("[[lat,lon],...]") a LatLng. Vacío si no es válido. */
internal fun parseWallPath(path: String?): List<LatLng> {
    if (path.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(path)
        (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONArray(i) ?: return@mapNotNull null
            if (p.length() < 2) null else LatLng(p.getDouble(0), p.getDouble(1))
        }
    } catch (e: Exception) { emptyList() }
}
