package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonSchoolRow
import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.ui.theme.terraFillColor
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.meteomontana.android.ui.guide.guideTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.ui.components.SchoolListItem
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import kotlinx.coroutines.flow.first

// Filas del modo «Vías/Bloques» de Escuelas (cabecera de grupo, fila de vía y mini-topo), fuera de SchoolListScreen.kt.

/**
 * Cabecera de grupo del modo "Vías/Bloques": nombre de escuela + score (de
 * HOY o combinado del tramo) + aviso MOJADA + contador — una sola vez por
 * grupo (BLOCK_SEARCH_DESIGN.md §8.3). Pulsable: pliega/despliega sus vías,
 * EMPIEZAN PLEGADOS para poder verlas todas de un vistazo.
 */
@Composable
internal fun ExploreGroupHeader(
    group: ExploreGroup,
    kind: com.meteomontana.android.domain.util.SchoolKind,
    score: Int?,
    dry: Boolean?,
    range: com.meteomontana.android.domain.model.RangeScore?,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val color = score?.let { com.meteomontana.android.ui.theme.scoreColor(it) } ?: MaterialTheme.colorScheme.outline
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 34.dp, height = 30.dp)
                    .background(color.copy(alpha = 0.12f))
                    .border(1.dp, color),
                contentAlignment = Alignment.Center
            ) {
                Text(score?.toString() ?: "—", style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold), color = color)
            }
            Spacer(Modifier.size(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.schoolName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    if (!kind.isEmpty) {
                        Spacer(Modifier.size(7.dp))
                        com.meteomontana.android.ui.components.SchoolKindIcons(kind, height = 15.dp)
                    }
                }
                if (dry == false) {
                    Text("● " + stringResource(R.string.schools_rock_wet),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error)
                }
            }
            val count = group.hits.size
            Text(
                if (count == 1) stringResource(R.string.explore_group_count_one)
                else stringResource(R.string.explore_group_count_other, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp
                else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Tramo de días: una puntuación por día, no solo la combinada —
        // mismo componente que ya usa la lista de Escuelas.
        if (range != null) {
            Spacer(Modifier.height(Spacing.xs))
            com.meteomontana.android.ui.components.DayRangeRow(range = range)
        }
    }
}

/**
 * Tipo de un resultado del buscador: manda la modalidad del propio resultado
 * (la trae el backend); si no llega (servidor viejo), el filtro activo
 * ("Bloque"/"Vía") y, por último, el estilo de su escuela — que solo sirve si es
 * de un único tipo: `ExploreLineRow` solo pinta icono si `isSingle`.
 */
internal fun exploreRowKind(discipline: String?, activeStyle: String?, schoolStyle: String?) =
    com.meteomontana.android.domain.util.SchoolKind.from(discipline ?: activeStyle ?: schoolStyle)

/** Fila de vía/bloque: mini-topo (foto + trazo, sin badge), nombre + grado
 *  coloreado, piedra/escuela, orientación o "SIN ORIENTACIÓN ASIGNADA",
 *  distancia. */
@Composable
internal fun ExploreLineRow(
    h: com.meteomontana.android.domain.model.LineSearchHit,
    kind: com.meteomontana.android.domain.util.SchoolKind,
    showSchool: Boolean,
    distanceKm: Int?,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        h.photoPath?.takeIf { it.isNotBlank() }?.let { photo ->
            MiniTopoThumbnail(photoUrl = photo, linePath = h.linePath, grade = h.grade,
                modifier = Modifier.size(44.dp).padding(end = Spacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((h.lineName ?: h.blockName), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                h.grade?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.size(6.dp))
                    if (com.meteomontana.android.ui.components.rememberLineSplit(h.lineId) != null) {
                        // La altura parte la opinión: el grado va en dos colores.
                        com.meteomontana.android.ui.screens.detail.GradePill(it, small = true, lineId = h.lineId)
                    } else {
                        val argb = com.meteomontana.android.domain.util.gradeArgb(it).first
                        Text(it, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color(argb.toInt()))
                    }
                }
                // Restricción en vigor (cierre por nidificación…): el detalle se ve al abrir la escuela.
                h.restrictionKind?.let {
                    Spacer(Modifier.size(6.dp))
                    com.meteomontana.android.ui.components.RestrictionMark(it, size = 14)
                }
                // Vía o bloque: solo cuando se sabe cuál es (ver exploreRowKind).
                if (kind.isSingle) {
                    Spacer(Modifier.size(6.dp))
                    com.meteomontana.android.ui.components.SchoolKindIcons(kind, height = 13.dp)
                }
            }
            val subtitle = listOfNotNull(
                h.blockName.takeIf { h.lineName != null },
                h.sectorName,
                if (showSchool) h.schoolName else null
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            // Vídeos de beta (por altura) y cuánta gente la ha hecho: se ve por qué sale con el filtro de ALTURA.
            com.meteomontana.android.ui.components.CommunityBadgesRow(h, Modifier.padding(vertical = 3.dp))
            if (!h.orientation.isNullOrBlank()) {
                Text(h.orientation!!, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.explore_no_orientation),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        distanceKm?.let {
            Text("$it km", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Miniatura de resultado: foto + trazo fino SIN badge de número/tipo de
 *  inicio (ese badge de TopoPhotoCanvas está pensado para el visor grande;
 *  a 44dp tapaba la foto entera — Álvaro, 2026-10-01: "se ve fatal"). Solo
 *  se intuye la línea. */
@Composable
internal fun MiniTopoThumbnail(photoUrl: String, linePath: String?, grade: String?, modifier: Modifier = Modifier) {
    val points = remember(linePath) {
        val raw = com.meteomontana.android.ui.screens.topo.parseLineStroke(linePath).points
        val out = mutableListOf<androidx.compose.ui.geometry.Offset>()
        raw.forEach { pt ->
            val last = out.lastOrNull()
            if (last == null || kotlin.math.abs(pt.x - last.x) + kotlin.math.abs(pt.y - last.y) > 0.004f) out.add(pt)
        }
        out
    }
    val argb = com.meteomontana.android.domain.util.gradeArgb(grade).first
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        coil.compose.AsyncImage(
            model = photoUrl, contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        if (points.size >= 2) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                val pts = points.map { androidx.compose.ui.geometry.Offset(it.x * size.width, it.y * size.height) }
                path.moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { path.lineTo(it.x, it.y) }
                drawPath(path, color = androidx.compose.ui.graphics.Color(argb.toInt()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}
