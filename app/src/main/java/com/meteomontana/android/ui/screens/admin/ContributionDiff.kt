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
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Moss
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.screens.detail.BoulderBloqueForm
import com.meteomontana.android.ui.screens.detail.ContributionTopoDialog
import com.meteomontana.android.ui.screens.detail.toBloquesJson
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import org.json.JSONArray
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style


// Helpers de la revision de propuestas: etiquetas, parseo de vias propuestas
// y diff de muros. Reparto del antiguo ContributionCard.kt de 955 lineas.

internal fun contributionTypeLabel(type: String): String = when (type) {
    "BOULDER" -> "PIEDRA"
    "PARKING" -> "PARKING"
    "SECTOR" -> "SECTOR"
    "ASSIGN_SECTOR" -> "SECTOR DE PIEDRA"
    "POSITION_CORRECTION" -> "MOVER"
    else -> type
}

/**
 * Resumen humano de UNA LÍNEA de qué cambia esta propuesta — el admin lo lee
 * arriba de la card sin tener que scrollear el detalle. Se apoya en los
 * bloques existentes de la escuela para nombrar la piedra/sector afectados.
 */
internal fun contributionSummary(
    c: Contribution,
    blocks: List<com.meteomontana.android.domain.model.Block>
): String {
    val target = blocks.firstOrNull { it.id == c.targetBlockId }
    return when (c.type) {
        "PARKING" -> "Añade un parking nuevo" +
            (c.name?.takeIf { it.isNotBlank() }?.let { " «$it»" } ?: "")
        "SECTOR" -> "Añade un sector nuevo" +
            (c.name?.takeIf { it.isNotBlank() }?.let { " «$it»" } ?: "")
        "ASSIGN_SECTOR" -> {
            val sector = blocks.firstOrNull { it.id == c.sectorBlockId }
            "Mueve «${target?.name ?: "una piedra"}» al sector «${sector?.name ?: "?"}»"
        }
        "POSITION_CORRECTION" -> {
            val what = if (c.targetBlockId.isNullOrBlank()) "la ESCUELA entera"
                       else "«${target?.name ?: c.name ?: "un elemento"}»"
            val meters = if (c.proposedLat != null && c.proposedLon != null)
                (com.meteomontana.android.domain.util.Geo.haversineKm(
                    c.lat, c.lon, c.proposedLat!!, c.proposedLon!!) * 1000).toInt()
            else null
            "Mueve $what" + (meters?.let { " unos $it m" } ?: "")
        }
        "BOULDER" -> {
            val vias = parseProposedVias(c.bloquesJson)
            val nuevas = vias.count { it.targetLineId == null }
            val corrige = vias.size - nuevas
            val esMuro = c.geometry.equals("LINE", true)
            if (target == null) {
                (if (esMuro) "Muro NUEVO" else "Piedra NUEVA") +
                    " con ${vias.size} vía${if (vias.size == 1) "" else "s"}"
            } else {
                buildString {
                    append(if (esMuro) "Muro" else "Piedra")
                    append(" «${target.name}»: ")
                    val parts = mutableListOf<String>()
                    if (nuevas > 0) parts.add("añade $nuevas vía${if (nuevas == 1) "" else "s"}")
                    if (corrige > 0) parts.add("corrige $corrige")
                    if (parts.isEmpty()) parts.add("cambios en el trazado/orden")
                    append(parts.joinToString(" y "))
                }
            }
        }
        else -> "Propuesta de tipo ${c.type}"
    }
}

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
                targetLineId = o.optString("targetLineId").takeIf { it.isNotEmpty() && it != "null" },
                description = o.optString("description").takeIf { it.isNotEmpty() && it != "null" },
                variant = o.optString("variant").takeIf { it.isNotEmpty() && it != "null" }
            )
        }
    } catch (_: Throwable) { emptyList() }
}

// ─── Diff de campos de una vía corregida (piedra POINT) ───────────────────────

/** True si dos trazados son iguales (mismos puntos, tolerancia mínima). */
internal fun pointsEqual(
    a: List<androidx.compose.ui.geometry.Offset>,
    b: List<androidx.compose.ui.geometry.Offset>
): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (kotlin.math.abs(a[i].x - b[i].x) > 0.001f) return false
        if (kotlin.math.abs(a[i].y - b[i].y) > 0.001f) return false
    }
    return true
}

/** ¿La vía propuesta cambia el DIBUJO (trazado) respecto a la existente? */
internal fun ProposedVia.drawingChangedFrom(
    orig: com.meteomontana.android.domain.model.BlockLine?
): Boolean {
    if (orig == null) return true // vía nueva
    val origPts = parseLineStroke(orig.linePath ?: "").points
    return !pointsEqual(this.points, origPts)
}

/** Una fila "Campo: viejo → nuevo" — solo se pinta si el campo cambia. */
@Composable
private fun FieldChangeRow(label: String, old: String?, new: String?) {
    val o = old?.takeIf { it.isNotBlank() }
    val n = new?.takeIf { it.isNotBlank() }
    if (o == n) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, top = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text("$label:", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${o ?: "—"} → ${n ?: "—"}",
            style = MaterialTheme.typography.bodyMedium, color = Terra)
    }
}

/**
 * Diff de UNA vía corregida/nueva. Si es nueva, la lista de campos; si corrige,
 * SOLO los campos que cambian (nombre/grado/variante/tipo/descripción) old→new,
 * y una nota si se redibujó el trazado. Así el admin ve qué toca sin mirar la
 * foto dos veces.
 */
@Composable
internal fun ViaChangeRows(
    orig: com.meteomontana.android.domain.model.BlockLine?,
    v: ProposedVia
) {
    if (orig == null) {
        val txt = listOfNotNull(
            v.name?.takeIf { it.isNotBlank() }, v.grade,
            v.variant?.let { "($it)" }, v.startType, v.description
        ).joinToString(" · ")
        Text("• NUEVA: $txt",
            style = MaterialTheme.typography.bodyMedium, color = Terra)
        return
    }
    val name = v.name?.takeIf { it.isNotBlank() } ?: orig.name
    val anyChange = orig.name != v.name || orig.grade != v.grade ||
        orig.variant != v.variant || orig.startType != v.startType ||
        orig.lineDescription != v.description || v.drawingChangedFrom(orig)
    Text("• $name" + if (!anyChange) "  (sin cambios)" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = if (anyChange) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant)
    FieldChangeRow("Nombre", orig.name, v.name)
    FieldChangeRow("Grado", orig.grade, v.grade)
    FieldChangeRow("Variante", orig.variant, v.variant)
    FieldChangeRow("Tipo", orig.startType, v.startType)
    FieldChangeRow("Descripción", orig.lineDescription, v.description)
    if (v.drawingChangedFrom(orig)) {
        Text("    Trazado: redibujado (ver foto)",
            modifier = Modifier.padding(start = Spacing.md),
            style = MaterialTheme.typography.bodyMedium, color = Terra)
    }
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
