@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
            androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.model.Submission
import androidx.compose.runtime.key
import com.meteomontana.android.ui.components.FullScreenMapDialog
import com.meteomontana.android.ui.components.TopoPhotoCanvas
import com.meteomontana.android.ui.components.parseBloquesJson
import com.meteomontana.android.ui.components.toTopoLines
import com.meteomontana.android.ui.components.pinBitmap
import com.meteomontana.android.ui.components.pinBitmapBoulder
import org.maplibre.android.annotations.IconFactory
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Moss
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import org.json.JSONArray
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style


@Composable
internal fun SubmissionCard(
    s: Submission,
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                .padding(horizontal = Spacing.sm, vertical = 2.dp)) {
                Text("ESCUELA", style = EyebrowTextStyle, color = Color.White)
            }
            Text(s.proposedName, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "${"%.5f".format(s.proposedLat)}, ${"%.5f".format(s.proposedLon)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        s.notes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                .clickable { onReject(s.id, null) }.padding(Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text("RECHAZAR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.error)
            }
            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(2.dp))
                .background(Moss).clickable { onApprove(s.id) }.padding(Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text("APROBAR", style = EyebrowTextStyle, color = Color.White)
            }
        }
    }
}

/** Lista compacta de bloques parseando el JSON de la contribución. */
@Composable
internal fun BloquesSummary(bloquesJson: String) {
    data class BloqueInfo(val name: String, val grade: String?, val startType: String?)

    val bloques = remember(bloquesJson) {
        try {
            val arr = JSONArray(bloquesJson)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BloqueInfo(
                    name = o.optString("name", ""),
                    grade = o.optString("grade").ifEmpty { null }.takeIf { it != "null" },
                    startType = o.optString("startType").ifEmpty { null }.takeIf { it != "null" }
                )
            }
        } catch (_: Throwable) { emptyList() }
    }

    if (bloques.isEmpty()) return

    Text("BLOQUES", style = EyebrowTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        bloques.forEachIndexed { idx, b ->
            val style = gradeStyle(b.grade)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(style.stroke)
                )
                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (b.grade != null) {
                    Text(b.grade, style = MaterialTheme.typography.labelMedium,
                        color = style.stroke)
                }
                if (b.startType != null) {
                    Text("· ${b.startType}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (b.name.isNotBlank()) {
                    Text("\"${b.name}\"", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private const val OSM_STYLE = """{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}"""
private const val TOPO_STYLE = """{"version":8,"sources":{"topo":{"type":"raster","tiles":["https://a.tile.opentopomap.org/{z}/{x}/{y}.png","https://b.tile.opentopomap.org/{z}/{x}/{y}.png","https://c.tile.opentopomap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenTopoMap (CC-BY-SA)"}},"layers":[{"id":"topo","type":"raster","source":"topo"}]}"""
private const val SAT_STYLE  = """{"version":8,"sources":{"sat":{"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"Tiles © Esri"}},"layers":[{"id":"sat","type":"raster","source":"sat"}]}"""
internal fun adminStyleJson(id: String): String = when (id) {
    "sat"  -> SAT_STYLE
    else   -> TOPO_STYLE
}

/** Re-dibuja todos los markers de una contribución sobre un mapa (tras cambio de estilo). */
internal fun redrawContributionMarkers(
    ctx: android.content.Context,
    map: org.maplibre.android.maps.MapLibreMap,
    c: Contribution,
    existingBlocks: List<com.meteomontana.android.domain.model.Block>
) {
    val iconFactory = IconFactory.getInstance(ctx)
    // Bloques existentes
    existingBlocks.forEach { b ->
        val icon = when (b.type) {
            "PARKING" -> pinBitmap(android.graphics.Color.parseColor("#1D6DD6"), "P", 28)
            "ZONE"    -> pinBitmap(android.graphics.Color.parseColor("#1FA84E"), "Z", 28)
            else      -> pinBitmapBoulder(
                label = b.name.takeIf { it.isNotBlank() } ?: "?",
                fillColor = android.graphics.Color.parseColor("#C2410C"),
                sizeDp = 28
            )
        }
        map.addMarker(MarkerOptions().position(LatLng(b.lat, b.lon)).title(b.name).icon(iconFactory.fromBitmap(icon)))
    }
    val pLat = c.proposedLat
    val pLon = c.proposedLon
    if (c.type == "POSITION_CORRECTION" && pLat != null && pLon != null) {
        val oldIcon = pinBitmap(android.graphics.Color.parseColor("#8A8478"), "✕", 36)
        val newIcon = pinBitmap(android.graphics.Color.parseColor("#F59E0B"), "★", 40)
        map.addMarker(MarkerOptions().position(LatLng(c.lat, c.lon)).title("ACTUAL").icon(iconFactory.fromBitmap(oldIcon)))
        map.addMarker(MarkerOptions().position(LatLng(pLat, pLon)).title("NUEVA").icon(iconFactory.fromBitmap(newIcon)))
        map.addPolyline(
            org.maplibre.android.annotations.PolylineOptions()
                .add(LatLng(c.lat, c.lon)).add(LatLng(pLat, pLon))
                .color(android.graphics.Color.parseColor("#C2410C")).width(3f)
        )
    } else {
        val icon = pinBitmap(android.graphics.Color.parseColor("#F59E0B"), "★", 40)
        map.addMarker(MarkerOptions().position(LatLng(c.lat, c.lon))
            .title("PROPUESTA · ${c.name ?: c.type}")
            .icon(iconFactory.fromBitmap(icon)))
    }
    // Muro: polilínea vieja (gris) + nueva (sólida terra).
    if (c.geometry.equals("LINE", true)) {
        val targetBlock = if (!c.targetBlockId.isNullOrBlank())
            existingBlocks.firstOrNull { it.id == c.targetBlockId } else null
        drawWallDiffPolylines(map, c, targetBlock)
    }
}

// ─────────────────────────── GESTIONAR ────────────────────────────

/**
 * Tab "GESTIONAR" — el admin busca una escuela y abre su mapa interactivo
 * con todos los bloques. Desde el mapa puede tocar cada bloque y borrarlo.
 */
