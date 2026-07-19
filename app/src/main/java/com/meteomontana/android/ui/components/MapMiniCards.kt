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
 * PIEZAS DE UI del mapa de escuela: buscador de vias, botonera lateral,
 * iconos de leyenda y mini-ficha flotante de parking/sector.
 *
 * Unica responsabilidad: presentacion (Compose puro). Nada de aqui toca
 * MapLibre directamente — reciben datos y callbacks.
 */

/** Buscador de vías/bloques de la escuela (visible solo con el mapa abierto). */
@Composable
internal fun SchoolViaSearchBar(
    blocks: List<Block>,
    viewModel: SchoolDetailViewModel
) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar vías/bloques…") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor   = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        val q = query.trim()
        if (q.length >= 2) {
            data class Hit(val label: String, val sub: String, val lineId: String?, val name: String)
            val hits = buildList {
                blocks.filter { it.type == "BLOCK" }.forEach { b ->
                    b.lines.forEach { l ->
                        if (l.name.contains(q, ignoreCase = true)) add(Hit(
                            label = l.name + (l.grade?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            sub = b.name, lineId = l.id, name = l.name))
                    }
                    if (b.name.contains(q, ignoreCase = true)) add(Hit(
                        label = b.name, sub = "${b.lines.size} vías", lineId = null, name = b.name))
                }
            }.take(8)
            if (hits.isEmpty()) {
                Text("Sin resultados en esta escuela",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                ) {
                    hits.forEach { h ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    viewModel.openVia(h.lineId, h.name)
                                    query = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(h.label, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                                modifier = Modifier.weight(1f))
                            Text(h.sub, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Botón circular de la botonera lateral del mapa (maqueta B): contenedor
 * blanco con el contenido dentro; apagado = semitransparente (capa oculta).
 */
@Composable
internal fun SideMapButton(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline,
                androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick)
            .graphicsLayer { alpha = if (active) 1f else 0.4f },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** P cuadrada azul — la forma real del marcador de parking. */
@Composable
internal fun ParkingShapeIcon() {
    Box(
        modifier = Modifier.size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1A56DB)),
        contentAlignment = Alignment.Center
    ) {
        Text("P", color = Color.White, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold)
    }
}

/** Polígono irregular terra — la forma real del marcador de piedra. */
@Composable
internal fun StoneShapeIcon() {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0.16f * w, 0.78f * h)
            lineTo(0.07f * w, 0.42f * h)
            lineTo(0.30f * w, 0.13f * h)
            lineTo(0.72f * w, 0.08f * h)
            lineTo(0.94f * w, 0.38f * h)
            lineTo(0.86f * w, 0.74f * h)
            lineTo(0.54f * w, 0.92f * h)
            close()
        }
        drawPath(path, Color(0xFFC2410C))
        drawPath(path, Color.White,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}

/** Círculo verde con Z — la forma real del marcador de zona/sector. */
@Composable
internal fun ZoneShapeIcon() {
    Box(
        modifier = Modifier.size(22.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color(0xFF3F6B4A)),
        contentAlignment = Alignment.Center
    ) {
        Text("Z", color = Color.White, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mini-ficha flotante de parking/sector sobre el mapa. Una sola línea de alto:
// icono + nombre + subtítulo, y a la derecha (admin: ✎ 🗑) + CÓMO LLEGAR + ✕.
// Para sectores con piedras añade el toggle VER/OCULTAR PIEDRAS explícito.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun MiniBlockCard(
    block: Block,
    stoneCount: Int,
    collapsed: Boolean,
    userLoc: com.meteomontana.android.domain.model.UserLocation?,
    isAdmin: Boolean,
    onToggleStones: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val isParking = block.type == "PARKING"
    val badgeColor = if (isParking) Color(0xFF1A56DB) else Color(0xFF3F6B4A)
    val distance = userLoc?.let { loc ->
        val km = Geo.haversineKm(loc.lat, loc.lon, block.lat, block.lon)
        if (km < 1.0) "${(km * 1000).toInt()} m" else "${"%.1f".format(km)} km"
    }
    val subtitle = buildString {
        append(if (isParking) "Parking" else "Sector")
        if (!isParking && stoneCount > 0) append(" · $stoneCount piedra${if (stoneCount == 1) "" else "s"}")
        distance?.let { append(" · $it") }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier.size(24.dp)
                    .clip(if (isParking) RoundedCornerShape(6.dp) else androidx.compose.foundation.shape.CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isParking) "P" else "Z", color = Color.White,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(block.name.ifBlank { if (isParking) "Parking" else "Sector" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (isAdmin) {
                Text("✎", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit).padding(Spacing.xs))
                Text("🗑", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDelete).padding(Spacing.xs))
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Terra)
                    .clickable {
                        runCatching {
                            ctx.startActivity(android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "https://www.google.com/maps/dir/?api=1&destination=${block.lat},${block.lon}")
                            ))
                        }
                    }
                    .padding(horizontal = Spacing.sm, vertical = 7.dp)
            ) {
                Text("CÓMO LLEGAR", style = EyebrowTextStyle, color = Color.White)
            }
            Text("✕", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose).padding(Spacing.xs))
        }
        // Toggle explícito de piedras del sector (antes: tap "silencioso" en el marker).
        if (!isParking && stoneCount > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
                    .clickable(onClick = onToggleStones)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (collapsed) "VER PIEDRAS" else "OCULTAR PIEDRAS",
                    style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
