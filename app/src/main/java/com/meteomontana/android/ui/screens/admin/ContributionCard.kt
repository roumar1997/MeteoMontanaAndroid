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
import com.meteomontana.android.domain.usecase.walls.WallRouteStatus
import androidx.compose.runtime.key
import com.meteomontana.android.ui.components.FullScreenMapDialog
import com.meteomontana.android.ui.components.TopoLine
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
internal fun ContributionCard(
    c: Contribution,
    existingBlocks: List<com.meteomontana.android.domain.model.Block>,
    onFetchBlocks: () -> Unit,
    onDeleteBlock: (String) -> Unit,
    onUpdateBlock: (com.meteomontana.android.domain.model.Block, com.meteomontana.android.data.api.dto.CreateBlockRequest) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit
) {
    val onUpdateBlockCard = onUpdateBlock
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var showFullMap by remember { mutableStateOf(false) }

    // Carga bloques existentes de la escuela al renderizar la card (una vez por schoolId).
    LaunchedEffect(c.schoolId) { onFetchBlocks() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(Spacing.md)
    ) {
        // Cabecera: badge tipo + escuela + tiempo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(Terra)
                    .padding(horizontal = Spacing.sm, vertical = 2.dp)
            ) {
                Text(c.type, style = EyebrowTextStyle, color = Color.White)
            }
            Text(c.schoolName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Text("ahora",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(Spacing.sm))

        // Nombre propuesto
        c.name?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }

        // Notas
        c.notes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }

        // Coordenadas
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "${"%.6f".format(c.lat)}, ${"%.6f".format(c.lon)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Autor
        c.submittedByName?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text("por $it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── BOULDER: revisión por CARA, comparando ACTUAL vs PROPUESTA ──────────
        if (c.type == "BOULDER") {
            val targetBlock = if (!c.targetBlockId.isNullOrBlank())
                existingBlocks.firstOrNull { it.id == c.targetBlockId } else null
            val proposed = parseProposedVias(c.bloquesJson)

            if (targetBlock != null && proposed.isNotEmpty()) {
                // Corrección/edición de una piedra existente: comparamos cara a cara.
                val existingFaces = targetBlock.facesOrDerived()
                // Agrupamos la propuesta por foto (cara).
                val groups = proposed.groupBy { it.photoUrl }
                groups.forEach { (facePhotoKey, vias) ->
                    val targetIds = vias.mapNotNull { it.targetLineId }.toSet()
                    val oldFace = existingFaces.firstOrNull { f -> f.lines.any { it.id in targetIds } }
                        ?: existingFaces.firstOrNull { it.photoPath == facePhotoKey }
                        ?: existingFaces.firstOrNull()
                    val oldPhoto = oldFace?.photoPath ?: targetBlock.photoPath
                    val photoChanged = !facePhotoKey.isNullOrBlank() && facePhotoKey != oldPhoto

                    Spacer(Modifier.height(Spacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(Spacing.sm))

                    // FOTO ACTUAL (estado vigente de esa cara).
                    if (!oldPhoto.isNullOrBlank()) {
                        Text(if (photoChanged) "FOTO ACTUAL" else "ACTUAL",
                            style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.xs))
                        TopoPhotoCanvas(
                            photoUrl = oldPhoto,
                            lines = (oldFace?.lines ?: emptyList()).toTopoLines()
                        )
                    }

                    Spacer(Modifier.height(Spacing.sm))
                    // PROPUESTA (foto nueva si la cambió + líneas resultantes).
                    Text(if (photoChanged) "FOTO PROPUESTA (NUEVA)" else "PROPUESTA",
                        style = EyebrowTextStyle, color = Terra)
                    Spacer(Modifier.height(Spacing.xs))
                    val proposedLines: List<TopoLine> = if (photoChanged) {
                        // Foto nueva: el proponente repinta TODAS las vías de la cara.
                        vias.map { it.toTopoLine() }
                    } else {
                        // Misma foto: vías sin tocar + las corregidas/nuevas propuestas.
                        val keep = (oldFace?.lines ?: emptyList())
                            .filter { it.id !in targetIds }.toTopoLines()
                        keep + vias.map { it.toTopoLine() }
                    }
                    val propostaPhoto = facePhotoKey ?: oldPhoto ?: ""
                    if (propostaPhoto.isNotEmpty()) {
                        TopoPhotoCanvas(photoUrl = propostaPhoto, lines = proposedLines)
                    }

                    // Texto: qué cambia en cada vía (original → propuesta).
                    Spacer(Modifier.height(Spacing.xs))
                    vias.forEach { v ->
                        val orig = v.targetLineId?.let { id -> oldFace?.lines?.firstOrNull { it.id == id } }
                        val newTxt = listOfNotNull(v.name, v.grade, v.startType).joinToString(" · ")
                        if (orig != null) {
                            val origTxt = listOfNotNull(orig.name, orig.grade, orig.startType?.toString()).joinToString(" · ")
                            Text("• $origTxt  →  $newTxt",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                        } else {
                            Text("• NUEVA: $newTxt",
                                style = MaterialTheme.typography.bodyMedium, color = Terra)
                        }
                    }
                }
            } else {
                // Piedra NUEVA (sin bloque destino): foto propuesta + sus líneas.
                val newPhoto = c.photoUrl
                if (!newPhoto.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.sm))
                    TopoPhotoCanvas(photoUrl = newPhoto, lines = parseBloquesJson(c.bloquesJson))
                }
                c.bloquesJson?.takeIf { it.isNotBlank() }?.let { BloquesSummary(it) }
            }
        }

        // ── MURO: diff de orden/numeración/dirección/trazado ────────────────
        if (c.geometry.equals("LINE", true)) {
            val targetBlock = if (!c.targetBlockId.isNullOrBlank())
                existingBlocks.firstOrNull { it.id == c.targetBlockId } else null
            WallDiffSection(c, targetBlock)
        }

        // ── ASSIGN_SECTOR: piedra X → sector Y ──────────────────────────────
        if (c.type == "ASSIGN_SECTOR") {
            val targetBlock = existingBlocks.firstOrNull { it.id == c.targetBlockId }
            val targetSector = existingBlocks.firstOrNull { it.id == c.sectorBlockId }
            Spacer(Modifier.height(Spacing.sm))
            Column(modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                .padding(Spacing.sm)) {
                Text("ASIGNAR SECTOR A PIEDRA", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(Spacing.xs))
                Text("PIEDRA", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(targetBlock?.name ?: "(no encontrada · ${c.targetBlockId})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.xs))
                Text("→ SECTOR PROPUESTO", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(targetSector?.name ?: "(no encontrado · ${c.sectorBlockId})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Terra)
            }
        }

        // ── Detalles textuales para POSITION_CORRECTION ─────────────────────
        if (c.type == "POSITION_CORRECTION") {
            Spacer(Modifier.height(Spacing.sm))
            Column(modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.06f))
                .padding(Spacing.sm)) {
                Text("POSICIÓN ACTUAL", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.6f, %.6f".format(c.lat, c.lon),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.proposedLat != null && c.proposedLon != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text("PROPONE MOVER A", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.6f, %.6f".format(c.proposedLat, c.proposedLon),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                        color = Terra)
                }
            }
        }

        // Mini-mapa con selector de estilo (topo/satélite)
        Spacer(Modifier.height(Spacing.sm))
        var mapStyle by remember { mutableStateOf("topo") }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf("topo" to "Topográfico", "sat" to "Satélite").forEach { (id, label) ->
                val selected = mapStyle == id
                Box(modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable {
                        mapStyle = id
                        mapViewRef.value?.let { mv ->
                            mv.getMapAsync { map ->
                                map.setStyle(Style.Builder().fromJson(adminStyleJson(id))) {
                                    map.clear()
                                    redrawContributionMarkers(mv.context, map, c, existingBlocks)
                                }
                            }
                        }
                    }
                    .padding(horizontal = Spacing.md, vertical = 4.dp)
                ) {
                    Text(label, style = EyebrowTextStyle,
                        color = if (selected) MaterialTheme.colorScheme.background
                                else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        com.meteomontana.android.ui.components.MapViewLifecycleEffect(mapViewRef)
        // key() re-crea el mini-mapa cuando llegan los bloques existentes
        key(existingBlocks.size) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(220.dp),
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
                            map.setStyle(Style.Builder().fromJson(adminStyleJson(mapStyle))) {
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(c.lat, c.lon)).zoom(15.0).build()

                                val iconFactory = IconFactory.getInstance(context)
                                // Bloques existentes: parking=círculo azul P, zone=círculo verde Z,
                                // block=polígono terra con el nombre de la piedra
                                existingBlocks.forEach { b ->
                                    val icon = when (b.type) {
                                        "PARKING" -> pinBitmap(android.graphics.Color.parseColor("#1D6DD6"), "P", 28)
                                        "ZONE"    -> pinBitmap(android.graphics.Color.parseColor("#1FA84E"), "Z", 28)
                                        else      -> pinBitmapBoulder(
                                            label = b.name.takeIf { it.isNotBlank() } ?: "?",
                                            fillColor = android.graphics.Color.parseColor("#C2410C"),
                                            sizeDp = 36
                                        )
                                    }
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(b.lat, b.lon))
                                            .title("[${b.type}] ${b.name}")
                                            .icon(iconFactory.fromBitmap(icon))
                                    )
                                }
                                // Marker de la propuesta (amarillo destacado).
                                // Si es BOULDER, usa forma de piedra; si no, círculo.
                                val proposalIcon = if (c.type == "BOULDER") {
                                    pinBitmapBoulder(
                                        label = c.name?.takeIf { it.isNotBlank() } ?: "★",
                                        fillColor = android.graphics.Color.parseColor("#F59E0B"),
                                        sizeDp = 42
                                    )
                                } else {
                                    pinBitmap(android.graphics.Color.parseColor("#F59E0B"), "★", 40)
                                }
                                // Para POSITION_CORRECTION: marker GRIS en posición vieja,
                                // marker AMARILLO en posición nueva propuesta, + línea entre ambos.
                                val pLat = c.proposedLat
                                val pLon = c.proposedLon
                                if (c.type == "POSITION_CORRECTION" && pLat != null && pLon != null) {
                                    val oldIcon = pinBitmap(android.graphics.Color.parseColor("#8A8478"), "✕", 36)
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(c.lat, c.lon))
                                            .title("POSICIÓN ACTUAL")
                                            .icon(iconFactory.fromBitmap(oldIcon))
                                    )
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(pLat, pLon))
                                            .title("PROPUESTA · ${c.name ?: "Nueva posición"}")
                                            .icon(iconFactory.fromBitmap(proposalIcon))
                                    )
                                    map.addPolyline(
                                        org.maplibre.android.annotations.PolylineOptions()
                                            .add(LatLng(c.lat, c.lon))
                                            .add(LatLng(pLat, pLon))
                                            .color(android.graphics.Color.parseColor("#C2410C"))
                                            .width(3f)
                                    )
                                    // Auto-fit: bounds entre los dos puntos con padding.
                                    val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                                        .include(LatLng(c.lat, c.lon))
                                        .include(LatLng(pLat, pLon))
                                        .build()
                                    map.animateCamera(
                                        org.maplibre.android.camera.CameraUpdateFactory
                                            .newLatLngBounds(bounds, 120)
                                    )
                                } else {
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(c.lat, c.lon))
                                            .title("PROPUESTA · ${c.name ?: c.type}")
                                            .icon(iconFactory.fromBitmap(proposalIcon))
                                    )
                                }
                                // Muro: polilínea vieja (gris) + nueva (sólida terra).
                                if (c.geometry.equals("LINE", true)) {
                                    val targetBlock = if (!c.targetBlockId.isNullOrBlank())
                                        existingBlocks.firstOrNull { it.id == c.targetBlockId } else null
                                    drawWallDiffPolylines(map, c, targetBlock)
                                }
                            }
                            map.uiSettings.apply {
                                isScrollGesturesEnabled  = true
                                isZoomGesturesEnabled    = true
                                isRotateGesturesEnabled  = false
                                isTiltGesturesEnabled    = false
                            }
                        }
                        onStart(); onResume()
                    }
                }
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        // Botones: VER EN MAPA | RECHAZAR | APROBAR
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // VER EN MAPA → mapa interactivo dentro de la app
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable {
                        onFetchBlocks()  // carga bloques existentes de la escuela
                        showFullMap = true
                    }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("VER EN MAPA", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            // RECHAZAR
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                    .clickable { onReject(c.id, null) }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("RECHAZAR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.error)
            }
            // APROBAR
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Moss)
                    .clickable { onApprove(c.id) }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("APROBAR", style = EyebrowTextStyle, color = Color.White)
            }
        }
    }

    // Mapa a pantalla completa al pulsar "VER EN MAPA"
    if (showFullMap) {
        val pLat = c.proposedLat
        val pLon = c.proposedLon
        val positionCorr = if (c.type == "POSITION_CORRECTION" && pLat != null && pLon != null)
                pLat to pLon else null
        FullScreenMapDialog(
            lat = c.lat,
            lon = c.lon,
            markerTitle = c.name ?: "${c.type} · ${c.schoolName}",
            existingBlocks = existingBlocks,
            // En POSITION_CORRECTION no queremos el "proposal-as-block" porque dibujaría
            // el marker estrella en (lat,lon) que es la posición VIEJA. Lo manejamos con
            // positionCorrectionNew para dibujar vieja + nueva + línea.
            proposalAsBlock = if (positionCorr == null) c.toFakeBlock() else null,
            positionCorrectionNew = positionCorr,
            positionCorrectionTargetName = c.name,
            onDeleteBlock = onDeleteBlock,
            onUpdateBlock = onUpdateBlockCard,
            onDismiss = { showFullMap = false }
        )
    }
}

/** Una vía propuesta en una corrección, con su cara (photoUrl) y a qué vía
 *  existente corrige (targetLineId, null = nueva). */
internal data class ProposedVia(
    val name: String?, val grade: String?, val startType: String?,
    val points: List<androidx.compose.ui.geometry.Offset>,
    val photoUrl: String?, val targetLineId: String?
)

internal fun ProposedVia.toTopoLine(): TopoLine = TopoLine(name, grade, startType, points)

/** Parsea el `bloquesJson` conservando photoUrl y targetLineId por vía. */
internal fun parseProposedVias(bloquesJson: String?): List<ProposedVia> {
    if (bloquesJson.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(bloquesJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ProposedVia(
                name = o.optString("name").takeIf { it.isNotEmpty() && it != "null" },
                grade = o.optString("grade").takeIf { it.isNotEmpty() && it != "null" },
                startType = o.optString("startType").takeIf { it.isNotEmpty() && it != "null" },
                points = com.meteomontana.android.ui.screens.topo.parseLineStroke(o.optString("linePath")).points,
                photoUrl = o.optString("photoUrl").takeIf { it.isNotEmpty() && it != "null" },
                targetLineId = o.optString("targetLineId").takeIf { it.isNotEmpty() && it != "null" }
            )
        }
    } catch (_: Throwable) { emptyList() }
}

// ─── Diff de muro (Fase 7) ────────────────────────────────────────────────────

/** Dibuja en el mapa la polilínea ACTUAL del muro (gris) y la PROPUESTA (terra). */
internal fun drawWallDiffPolylines(
    map: org.maplibre.android.maps.MapLibreMap,
    c: Contribution,
    targetBlock: com.meteomontana.android.domain.model.Block?
) {
    val oldPath = com.meteomontana.android.ui.components.parseWallPath(targetBlock?.path)
    if (oldPath.size >= 2) {
        map.addPolyline(
            org.maplibre.android.annotations.PolylineOptions()
                .addAll(oldPath)
                .color(android.graphics.Color.parseColor("#8A8478"))
                .width(6f).alpha(0.6f)
        )
    }
    val newPath = com.meteomontana.android.ui.components.parseWallPath(c.path)
    if (newPath.size >= 2) {
        map.addPolyline(
            org.maplibre.android.annotations.PolylineOptions()
                .addAll(newPath)
                .color(android.graphics.Color.parseColor("#C2410C"))
                .width(5f)
        )
    }
}

/** Construye el diff del muro: estado actual (targetBlock) vs propuesta (c). */
internal fun buildWallDiff(
    c: Contribution,
    targetBlock: com.meteomontana.android.domain.model.Block?
): com.meteomontana.android.domain.usecase.walls.WallDiff {
    val existing = targetBlock?.lines
        ?.sortedWith(compareBy({ it.faceOrder }, { it.sortOrder }))
        ?.map { com.meteomontana.android.domain.usecase.walls.ExistingRoute(it.id, it.name, it.grade) }
        ?: emptyList()
    val proposed = parseProposedVias(c.bloquesJson)
        .map { com.meteomontana.android.domain.usecase.walls.ProposedRoute(it.targetLineId, it.name, it.grade) }
    val pathChanged = targetBlock != null && !c.path.isNullOrBlank() && c.path != targetBlock.path
    return com.meteomontana.android.domain.usecase.walls.WallDiffCalculator.compute(
        existing, proposed, pathChanged, targetBlock?.direction, c.direction
    )
}

private fun dirLabel(d: String?): String = if (d.equals("RTL", true)) "DER→IZQ" else "IZQ→DER"

@Composable
internal fun WallDiffSection(
    c: Contribution,
    targetBlock: com.meteomontana.android.domain.model.Block?
) {
    val diff = remember(c, targetBlock) { buildWallDiff(c, targetBlock) }
    Spacer(Modifier.height(Spacing.md))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(Spacing.sm))
    Text("MURO · CAMBIOS", style = EyebrowTextStyle, color = Terra)
    Spacer(Modifier.height(Spacing.xs))

    if (targetBlock == null) {
        Text("Muro NUEVO · numeración ${dirLabel(c.direction)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    } else {
        val dirChanged = (targetBlock.direction ?: "LTR") != (c.direction ?: "LTR")
        Text(
            if (dirChanged) "Dirección: ${dirLabel(targetBlock.direction)}  →  ${dirLabel(c.direction)}"
            else "Dirección: ${dirLabel(c.direction)} (sin cambio)",
            style = MaterialTheme.typography.bodyMedium,
            color = if (dirChanged) Terra else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (diff.pathChanged) {
            Text("⚠ El trazado/longitud del muro cambia (ver mapa: gris=actual, terra=propuesto)",
                style = MaterialTheme.typography.bodyMedium, color = Terra)
        }
    }

    Spacer(Modifier.height(Spacing.sm))
    diff.proposed.forEach { WallChangeRow(it) }
    diff.removed.forEach { WallChangeRow(it) }
}

@Composable
private fun WallChangeRow(ch: com.meteomontana.android.domain.usecase.walls.WallRouteChange) {
    val oldN = (ch.oldPos ?: 0) + 1
    val newN = (ch.newPos ?: 0) + 1
    val (label, color) = when (ch.status) {
        WallRouteStatus.NEW -> "NUEVA #$newN" to Moss
        WallRouteStatus.MOVED -> "MOVIDA #$oldN→#$newN" to Terra
        WallRouteStatus.MODIFIED -> "MODIFICADA #$newN" to Terra
        WallRouteStatus.MOVED_MODIFIED -> "MOVIDA+MODIF #$oldN→#$newN" to Terra
        WallRouteStatus.REMOVED -> "QUITADA #$oldN" to MaterialTheme.colorScheme.error
        WallRouteStatus.CONFLICT -> "CONFLICTO #$newN" to MaterialTheme.colorScheme.error
        WallRouteStatus.SAME -> "= #$newN" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(color)
                .padding(horizontal = Spacing.sm, vertical = 1.dp)
        ) {
            Text(label, style = EyebrowTextStyle, color = Color.White)
        }
        val name = ch.name?.takeIf { it.isNotBlank() } ?: "(sin nombre)"
        val gradeTxt = when (ch.status) {
            WallRouteStatus.MODIFIED, WallRouteStatus.MOVED_MODIFIED ->
                if (ch.oldGrade != ch.newGrade) "  ${ch.oldGrade ?: "—"} → ${ch.newGrade ?: "—"}" else ch.newGrade?.let { "  $it" } ?: ""
            WallRouteStatus.REMOVED -> ch.oldGrade?.let { "  $it" } ?: ""
            else -> ch.newGrade?.let { "  $it" } ?: ""
        }
        Text("$name$gradeTxt",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Convierte la contribución en un `BlockDto` "fantasma" para reusar el dialog
 * de detalles del mapa. Útil para que el admin vea exactamente lo mismo que
 * verá el usuario tras aprobar.
 */
internal fun Contribution.toFakeBlock(): com.meteomontana.android.domain.model.Block {
    val blockType = when (type) {
        "PARKING" -> "PARKING"
        "SECTOR"  -> "ZONE"
        else      -> "BLOCK"  // BOULDER y POSITION_CORRECTION
    }
    val lines = if (type == "BOULDER" && !bloquesJson.isNullOrBlank()) {
        try {
            val arr = JSONArray(bloquesJson)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.meteomontana.android.domain.model.BlockLine(
                    id = "proposal-line-$i",
                    name = o.optString("name", ""),
                    grade = o.optString("grade").takeIf { it.isNotEmpty() && it != "null" },
                    startType = o.optString("startType").takeIf { it.isNotEmpty() && it != "null" },
                    linePath = o.optString("linePath"),
                    sortOrder = i
                )
            }
        } catch (_: Throwable) { emptyList() }
    } else emptyList()
    return com.meteomontana.android.domain.model.Block(
        id = id,
        schoolId = schoolId,
        type = blockType,
        name = name?.takeIf { it.isNotBlank() } ?: "?",
        lat = lat, lon = lon,
        photoPath = photoUrl,
        description = notes,
        createdByUid = "",
        createdAt = createdAt ?: "",
        lines = lines
    )
}

