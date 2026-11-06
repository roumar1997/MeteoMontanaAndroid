package com.meteomontana.android.ui.components

import com.meteomontana.android.ui.theme.terraFillColor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.meteomontana.android.domain.util.SectorCercano
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
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import com.meteomontana.android.ui.theme.Serif
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.shape.CircleShape
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
    val closeKeyboard = rememberKeyboardDismisser()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        CumbreSearchField(query, { query = it }, stringResource(R.string.map_mini_cards_buscar_vias_bloques),
            onSearch = { closeKeyboard() })
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
                        label = b.name, sub = stringResource(R.string.grade_filter_bar_v3_vias, b.lines.size), lineId = null, name = b.name))
                }
            }.take(8)
            if (hits.isEmpty()) {
                Text(stringResource(R.string.map_mini_cards_v2_sin_resultados_en_esta_escuela),
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
                                    closeKeyboard()
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
// Tarjeta flotante de parking/sector sobre el mapa, como la de iOS: insignia P/S, nombre en serif, pastillas de tipo y
// distancia, X para cerrar, y debajo CÓMO LLEGAR (+ VER/OCULTAR PIEDRAS en un sector con piedras).
// Desde el parking se ofrecen los sectores, ordenados por lo que queda por andar.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun MiniBlockCard(
    block: Block,
    stoneCount: Int,
    collapsed: Boolean,
    userLoc: com.meteomontana.android.domain.model.UserLocation?,
    onToggleStones: () -> Unit,
    onClose: () -> Unit,
    /** Sectores de la escuela para ofrecerlos desde el PARKING, con su distancia DESDE EL PARKING. Vacío = sin fila. */
    sectores: List<SectorCercano> = emptyList(),
    onSectorClick: (Block) -> Unit = {},
    /** Camino grabado hasta aquí: «Seguir camino» sin ir a buscarlo a APROXIMACIONES. */
    approach: com.meteomontana.android.domain.model.Approach? = null,
    onFollowApproach: (com.meteomontana.android.domain.model.Approach) -> Unit = {},
    /** Piedra (desde «Ver en mapa» de la guía): abre su ficha completa. */
    onOpenStone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val isParking = block.type == "PARKING"
    val isStone = !isParking && block.type != "ZONE"
    val accent = if (isStone) Terra else if (isParking) Color(0xFF1A56DB) else Color(0xFF3F6B4A)
    val distance = userLoc?.let { loc ->
        val km = Geo.haversineKm(loc.lat, loc.lon, block.lat, block.lon)
        if (km < 1.0) "${(km * 1000).toInt()} m" else "${"%.1f".format(km)} km"
    }
    val kindText = buildString {
        append(if (isParking) "Parking" else "Sector")
        if (!isParking && stoneCount > 0) append(stringResource(R.string.map_mini_cards_v3_piedra, stoneCount, if (stoneCount == 1) "" else "s"))
    }.let { base -> if (isStone) stringResource(R.string.map_card_stone_lines, block.lines.size) else base }
    val shape = RoundedCornerShape(24.dp)
    val hasStonesButton = !isParking && !isStone && stoneCount > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent), contentAlignment = Alignment.Center) {
                Text(if (isParking) "P" else if (isStone) "◆" else "S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(block.name.ifBlank { kindText }, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardTag(kindText, accent)
                    distance?.let { CardTag(it, MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, stringResource(R.string.common_close), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardPill(stringResource(R.string.map_card_directions), Icons.Outlined.Navigation, primary = true, modifier = Modifier.weight(1f)) {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${block.lat},${block.lon}")))
                }
            }
            if (isStone) {
                CardPill(stringResource(R.string.map_card_open_stone), Icons.Outlined.Layers, modifier = Modifier.weight(1f)) {
                    onClose(); onOpenStone()
                }
            } else if (hasStonesButton) {
                CardPill(
                    stringResource(if (collapsed) R.string.map_card_show_stones else R.string.map_card_hide_stones),
                    if (collapsed) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    modifier = Modifier.weight(1f), onClick = onToggleStones
                )
            } else if (approach != null) {
                CardPill(stringResource(R.string.map_card_follow_path), Icons.AutoMirrored.Outlined.DirectionsWalk, modifier = Modifier.weight(1f)) {
                    onClose()
                    onFollowApproach(approach)
                }
            }
        }
        if (isParking && sectores.isNotEmpty()) {
            Text(stringResource(R.string.map_mini_cards_v2_sectores_desde_aqui), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                sectores.forEach { s ->
                    MochilaCard(
                        label = "${s.sector.name.ifBlank { "Sector" }} · ${s.distanciaTexto}" + if (s.piedras > 0) " · ${s.piedras}" else "",
                        selected = false
                    ) { onSectorClick(s.sector) }
                }
            }
        }
    }
}

@Composable
private fun CardTag(text: String, tint: Color) {
    Text(text, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.13f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun CardPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier,
                     primary: Boolean = false, onClick: () -> Unit) {
    val fg = if (primary) Color.White else MaterialTheme.colorScheme.onBackground
    Row(
        modifier.clip(CircleShape)
            .background(if (primary) terraFillColor() else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

