package com.meteomontana.android.ui.screens.detail.guide

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.aspectRatio
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.schools.GuideBlock
import com.meteomontana.android.domain.usecase.schools.GuideSector
import com.meteomontana.android.domain.usecase.schools.GuideView
import com.meteomontana.android.domain.util.gradeArgb
import com.meteomontana.android.ui.components.LocalSchoolRestrictions
import com.meteomontana.android.ui.components.RestrictionMarkButton
import com.meteomontana.android.ui.components.TopoLine
import com.meteomontana.android.ui.components.TopoPhotoCanvas
import com.meteomontana.android.ui.components.startTypeLabel
import com.meteomontana.android.ui.screens.detail.GradePill
import com.meteomontana.android.ui.screens.detail.SchoolBlocksViewModel
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Warn
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Lo que la GUÍA pide a la ficha (todo pasa FUERA de la pestaña): abrir la piedra, centrar el mapa, el diario.
 * Espejo de los callbacks de `SchoolGuideView` (iOS).
 */
class GuideActions(
    /** Abre la piedra de verdad (por su vía si se sabe); al cerrarla se vuelve a la guía, al mismo sitio. */
    val onOpen: (lineId: String?, blockId: String) -> Unit,
    /** "Ver en mapa": el mapa de la ficha centrado en la piedra, sin abrirla. */
    val onShowOnMap: (blockId: String) -> Unit,
    /** Hoja de ascenso para apuntar la vía como hecha. */
    val onTick: (LineSearchHit) -> Unit,
    /** Quitar la vía hecha del diario (ya confirmado). */
    val onUnmark: (LineSearchHit) -> Unit,
    /** Marcar / quitar como proyecto. */
    val onToggleProject: (LineSearchHit, markProject: Boolean) -> Unit
)

/** Baja la ficha hasta que lo que está en [yInWindow] quede arriba. */
fun interface GuideScroller { fun bringToTop(yInWindow: Float) }

@Composable
fun sectorTitle(s: GuideSector): String =
    if (s.unknownFace) stringResource(R.string.sguide_unknown_face) else s.sector ?: stringResource(R.string.blocks_no_sector)

/**
 * Modo GUÍA: la escuela como una guía de papel. Sector a sector (tarjetas plegables con tu progreso) y piedra a
 * piedra, en el orden en que te las encuentras andando (lo calcula `SchoolGuide`, shared). Espejo de `SchoolGuideView`.
 * [anchors] apunta dónde queda cada sector ("s:clave") y cada piedra ("b:id") en la pantalla, para bajar hasta ellos.
 */
@Composable
fun SchoolGuideSectors(
    guide: GuideView,
    state: SchoolBlocksViewModel.UiState,
    vm: SchoolBlocksViewModel,
    actions: GuideActions,
    anchors: MutableMap<String, Float>
) {
    var zoomed by remember { mutableStateOf<GuideFace?>(null) }
    var unmarking by remember { mutableStateOf<LineSearchHit?>(null) }

    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (guide.sectors.isEmpty()) {
            Text(stringResource(R.string.blocks_none_match), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
        }
        guide.sectors.forEach { s ->
            SectorCard(s, state, vm, actions, anchors, onZoom = { zoomed = it }, onUnmark = { unmarking = it })
        }
    }

    zoomed?.let { face -> ZoomDialog(face) { zoomed = null } }
    unmarking?.let { h ->
        AlertDialog(
            onDismissRequest = { unmarking = null },
            title = { Text(stringResource(R.string.sguide_unmark_title)) },
            confirmButton = {
                TextButton(onClick = { unmarking = null; actions.onUnmark(h) }) {
                    Text(stringResource(R.string.sguide_unmark), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { unmarking = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

// ── Sector ──

@Composable
private fun SectorCard(
    s: GuideSector, state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel, actions: GuideActions,
    anchors: MutableMap<String, Float>, onZoom: (GuideFace) -> Unit, onUnmark: (LineSearchHit) -> Unit
) {
    val open = s.key in state.guideOpen
    val shape = RoundedCornerShape(14.dp)
    val outline = MaterialTheme.colorScheme.outline
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { anchors["s:${s.key}"] = it.positionInWindow().y }
            .clip(shape).background(MaterialTheme.colorScheme.surface)
            .then(
                if (s.unknownFace) Modifier.dashedBorder(outline, 14.dp)
                else Modifier.border(1.dp, outline, shape)
            )
    ) {
        SectorHeader(s, state, open) { vm.toggleSector(s.key) }
        if (open) {
            // Como el PDF: foto del sector, todas sus vías por grado (tocar = ir a su piedra) y una tarjeta por piedra.
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectorIntro(s, state, vm)
                s.blocks.forEach { b ->
                    val cardShape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier.fillMaxWidth()
                            .onGloballyPositioned { anchors["b:${b.blockId}"] = it.positionInWindow().y }
                            .clip(cardShape).background(MaterialTheme.colorScheme.background)
                            .border(1.dp, outline, cardShape)
                    ) { BlockCard(b, state, vm, actions, onZoom, onUnmark) }
                }
            }
        }
    }
}

/** Borde discontinuo: el apartado "Sin orientación votada" (como en iOS). */
private fun Modifier.dashedBorder(color: Color, radius: androidx.compose.ui.unit.Dp) = drawBehind {
    val r = radius.toPx()
    drawRoundRect(
        color = color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    )
}

@Composable
private fun SectorHeader(s: GuideSector, state: SchoolBlocksViewModel.UiState, open: Boolean, onToggle: () -> Unit) {
    val done = s.doneCount(state.doneIds)
    val total = s.lineCount
    val pieces = if (s.blocks.size == 1) stringResource(R.string.sguide_blocks_one) else stringResource(R.string.sguide_blocks, s.blocks.size)
    val lines = if (total == 1) stringResource(R.string.sguide_lines_one) else stringResource(R.string.sguide_lines, total)
    Row(
        Modifier.fillMaxWidth().testTag("guide_sector").clickable(onClick = onToggle).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
            Modifier.size(18.dp).rotate(if (open) 90f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(sectorTitle(s), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                if (!s.unknownFace) {
                    val ui = LocalSchoolRestrictions.current
                    val sectorId = ui?.blocks?.firstOrNull { it.type == "ZONE" && it.name == s.sector }?.id
                    if (ui != null && sectorId != null) RestrictionMarkButton(ui.allInSector(sectorId))
                }
            }
            Text("$pieces · $lines · " + stringResource(R.string.sguide_done_of, done, total),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Progreso: verde sobre la raya.
        Box(Modifier.width(46.dp).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline)) {
            val frac = if (total == 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(frac).height(5.dp).clip(CircleShape).background(Ok))
        }
    }
}

// ── Inicio del sector (como el PDF) ──

/** Foto aérea del sector y TODAS sus vías agrupadas por grado; tocar una vía = bajar hasta su piedra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectorIntro(s: GuideSector, state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel) {
    val map = state.sectorMaps[s.key]
    if (map != null) {
        val img = remember(map) { map.asImageBitmap() }
        Image(img, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.FillWidth)
    } else {
        val photo = s.blocks.asSequence().flatMap { it.faces }.mapNotNull { it.photo }.firstOrNull()
        LaunchedEffect(s.key, state.overviewBlocks.size) { if (state.overviewBlocks.isNotEmpty()) vm.loadSectorMap(s) }
        // Mientras se hace la foto aérea, un hueco con brillo (antes salía una foto y de golpe cambiaba al mapa).
        if (s.key !in state.sectorMapsTried && state.overviewBlocks.isNotEmpty()) {
            com.meteomontana.android.ui.components.PhotoPlaceholder(
                Modifier.fillMaxWidth().aspectRatio(1100f / 520f).clip(RoundedCornerShape(14.dp)))
        } else if (photo != null) {
            AsyncImage(photo, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sguide_all_lines), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.linesByGrade().forEach { (g, chips) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(46.dp)) { GradePill(g) }
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    chips.forEach { c ->
                        val (argb, _, dark) = gradeArgb(c.hit.grade)
                        Row(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.background)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { vm.jumpToBlock(s.key, c.block.blockId) }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(argb.toInt()))
                                .then(if (dark) Modifier.border(0.8.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier))
                            Text(c.hit.lineName ?: c.block.blockName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.block.blockName, fontFamily = Mono, fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ── Piedra ──

@Composable
private fun BlockCard(
    b: GuideBlock, state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel, actions: GuideActions,
    onZoom: (GuideFace) -> Unit, onUnmark: (LineSearchHit) -> Unit
) {
    val ctx = LocalContext.current
    val faces = b.faces
    Column {
        // Una foto por cara, cada una con SUS vías debajo (numeradas como en la foto, igual que la ficha).
        faces.forEachIndexed { fi, face ->
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))) {
                // Tocar la foto = abrir la piedra de verdad; al cerrarla se vuelve aquí (marcapáginas).
                Box(Modifier.fillMaxWidth().clickable {
                    vm.setBookmark(b.blockId)
                    actions.onOpen(face.lines.firstOrNull()?.lineId, b.blockId)
                }) { FaceTopo(face) }
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.sguide_stone, b.blockName.uppercase()), color = Color.White,
                        fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 9.dp, vertical = 5.dp))
                    if (faces.size > 1) {
                        Text(stringResource(R.string.sguide_face_of, fi + 1, faces.size), color = Color(0xFF1C1C1A),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.9f))
                                .padding(horizontal = 9.dp, vertical = 5.dp))
                    }
                    if (fi == 0) LocalSchoolRestrictions.current?.let { RestrictionMarkButton(it.forBlockId(b.blockId)) }
                    Spacer(Modifier.weight(1f))
                    if (face.photo != null) {
                        val desc = stringResource(R.string.sguide_zoom_photo)
                        Icon(Icons.Outlined.OpenInFull, desc,
                            Modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                                .clickable { vm.setBookmark(b.blockId); onZoom(face) }.padding(8.dp),
                            tint = Color.White)
                    }
                }
            }
            face.lines.forEachIndexed { i, h -> LineRow(h, i + 1, state, vm, actions, onUnmark) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(stringResource(R.string.sguide_show_on_map), Icons.Outlined.Map, filled = false, Modifier.weight(1f)) {
                vm.setBookmark(b.blockId)
                actions.onShowOnMap(b.blockId)
            }
            val lat = b.lat
            val lon = b.lon
            if (lat != null && lon != null) {
                ActionButton(stringResource(R.string.sguide_directions), Icons.Outlined.Directions, filled = true, Modifier.weight(1f)) {
                    vm.setBookmark(b.blockId)
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon")))
                    }
                }
            }
        }
    }
}

/** Foto de una cara con sus líneas, con el MISMO pintor que la ficha de la piedra. */
@Composable
private fun FaceTopo(face: GuideFace, modifier: Modifier = Modifier) {
    val photo = face.photo
    if (photo == null) {
        Box(modifier.fillMaxWidth().height(60.dp))
        return
    }
    val lines = remember(face.id) {
        face.lines.map { h -> TopoLine(null, h.grade, h.startType, parseLineStroke(h.linePath).points) }
    }
    TopoPhotoCanvas(photoUrl = photo, lines = lines, modifier = modifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LineRow(
    h: LineSearchHit, number: Int, state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel,
    actions: GuideActions, onUnmark: (LineSearchHit) -> Unit
) {
    val id = h.lineId ?: ""
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            Modifier.weight(1f).clickable { vm.setBookmark(h.blockId); actions.onOpen(h.lineId, h.blockId) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            LineNumber(number, h.grade)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(h.lineName ?: h.blockName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                meta(h)?.let { Text(it, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                com.meteomontana.android.ui.components.CommunityBadgesRow(h)
                // El comentario de quien la subió (beta, salida, avisos), como en las guías de papel.
                h.lineDescription?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            GradePill(h.grade, lineId = h.lineId)
        }
        TickButton(h, done = id in state.doneIds, project = id in state.projectIds, vm, actions, onUnmark)
    }
}

/**
 * ✓ verde = hecha · ◐ naranja = proyecto · vacío = sin hacer. Tocar (sin hacer o proyecto) = hoja de ascenso;
 * tocar una hecha = quitarla (con confirmación). Mantener pulsado = marcar / quitar como proyecto.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TickButton(
    h: LineSearchHit, done: Boolean, project: Boolean, vm: SchoolBlocksViewModel, actions: GuideActions,
    onUnmark: (LineSearchHit) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(if (done) R.string.sguide_done else if (project) R.string.sguide_project else R.string.sguide_mark_done)
    val ring = if (done) Ok else if (project) Warn else MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(if (done) Ok else Color.Transparent)
            .border(1.5.dp, ring, CircleShape)
            .combinedClickable(
                onClick = {
                    vm.setBookmark(h.blockId)
                    if (done) onUnmark(h) else actions.onTick(h)
                },
                onLongClick = {
                    if (!done) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onToggleProject(h, !project)
                    }
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        when {
            done -> Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Color.White)
            project -> Box(Modifier.size(13.dp).clip(CircleShape).border(1.5.dp, Warn, CircleShape)) {
                Box(Modifier.fillMaxSize().padding(end = 6.5.dp).background(Warn))
            }
        }
    }
}

/** Número de la vía en su cara (el mismo que lleva la línea en la foto), con el color de su grado. */
@Composable
private fun LineNumber(number: Int, grade: String?) {
    val (argb, _, dark) = gradeArgb(grade)
    Box(Modifier.size(22.dp).clip(CircleShape).background(Color(argb.toInt())), contentAlignment = Alignment.Center) {
        Text("$number", color = if (dark) Color.Black else Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
    }
}

@Composable
private fun ActionButton(
    text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, modifier: Modifier,
    onClick: () -> Unit
) {
    val fill = terraFillColor()
    val fg = if (filled) Color.White else MaterialTheme.colorScheme.onBackground
    Row(
        modifier.clip(CircleShape).background(if (filled) fill else Color.Transparent)
            .border(1.dp, if (filled) fill else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = fg)
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/** "Sentado · ★ 4.6 · ✓ 12" solo si hay algo que contar. */
@Composable
private fun meta(h: LineSearchHit): String? {
    val parts = mutableListOf<String>()
    startTypeLabel(h.startType)?.let { parts.add(it) }
    val avg = h.rating
    if (avg != null && (h.ratingCount ?: 0) > 0) parts.add("★ ${"%.1f".format(avg)}")
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** La foto de la piedra en grande, con zoom de pellizco. */
@Composable
private fun ZoomDialog(face: GuideFace, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentAlignment = Alignment.Center
            ) { FaceTopo(face) }
            Icon(Icons.Outlined.Close, stringResource(R.string.common_close),
                Modifier.align(Alignment.TopEnd).padding(16.dp).size(34.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)).clickable(onClick = onDismiss).padding(8.dp),
                tint = Color.White)
        }
    }
}
