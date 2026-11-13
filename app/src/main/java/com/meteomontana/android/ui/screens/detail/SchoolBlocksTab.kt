package com.meteomontana.android.ui.screens.detail

import androidx.compose.material.icons.outlined.Check
import com.meteomontana.android.ui.theme.Terra
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.MenuBook
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.schools.BetaHeights
import com.meteomontana.android.domain.usecase.schools.SchoolLinesExplorer
import com.meteomontana.android.domain.util.gradeArgb
import com.meteomontana.android.ui.components.BETA_SOURCES
import com.meteomontana.android.ui.components.SkeletonGradeRow
import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.rememberLineSplit
import com.meteomontana.android.ui.guide.guideTarget
import com.meteomontana.android.ui.screens.detail.guide.GuideActions
import com.meteomontana.android.ui.screens.detail.guide.GuideOverview
import com.meteomontana.android.ui.screens.detail.guide.GuideScroller
import com.meteomontana.android.ui.screens.detail.guide.SchoolGuideSectors
import com.meteomontana.android.ui.screens.detail.guide.sectorTitle
import com.meteomontana.android.ui.screens.schools.MiniTopoThumbnail
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.terraFillColor
import kotlin.math.roundToInt

/**
 * Pestaña BLOQUES/VÍAS de la ficha de una escuela, en forma de GUÍA (espejo de `SchoolBlocksExplorerView` de iOS):
 * arriba el mapa de la escuela (plegado), el índice de sectores y el PDF; luego los filtros (buscador, cara, sin
 * hacer, y en "Más" grados —varios— y beta por altura); las DESTACADAS (más repetidas / mejor valoradas) y debajo la
 * guía sector a sector. Los filtros valen para todo.
 *
 * Esta vista SOLO pinta: el estado vive en [SchoolBlocksViewModel]; filtrar, ordenar y agrupar lo calculan
 * `SchoolLinesExplorer` y `SchoolGuide` (shared). Lo que pasa fuera de la pestaña (abrir la piedra, el diario, bajar
 * por la ficha) lo hace la ficha a través de [actions] y [scroller].
 */
@Composable
fun SchoolBlocksTab(
    viewModel: SchoolBlocksViewModel,
    schoolName: String,
    actions: GuideActions,
    scroller: GuideScroller
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    // Dónde está cada sector y cada piedra en la pantalla (para bajar hasta ellos).
    val anchors = remember { HashMap<String, Float>() }
    LaunchedEffect(Unit) {
        viewModel.jumps.collect { j ->
            // Que se componga y mida el sector recién desplegado (2 fotogramas) antes de leer su posición; antes, delay(160).
            repeat(2) { androidx.compose.runtime.withFrameNanos { } }
            val key = when (j) {
                is SchoolBlocksViewModel.Jump.Sector -> "s:${j.key}"
                is SchoolBlocksViewModel.Jump.Block -> "b:${j.blockId}"
            }
            anchors[key]?.let(scroller::bringToTop)
        }
    }
    // Al entrar en la guía todos los sectores salen cerrados: solo se abren al tocarlos (sin marcapáginas).

    // El PDF listo → hoja de compartir del sistema (guardar, WhatsApp, imprimir…).
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.pdfReady.collect { file ->
            if (file == null) {
                android.widget.Toast.makeText(ctx, R.string.sguide_pdf_failed, android.widget.Toast.LENGTH_LONG).show()
                return@collect
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { ctx.startActivity(Intent.createChooser(send, file.nameWithoutExtension)) }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        when {
            state.loading -> SkeletonList(count = 6) { SkeletonGradeRow() }
            state.failed -> Column(
                Modifier.fillMaxWidth().heightIn(min = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.blocks_load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.common_retry).uppercase(),
                    color = MaterialTheme.colorScheme.primary, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    modifier = Modifier.clickable { viewModel.loadIfNeeded(force = true) }.padding(10.dp)
                )
            }
            !state.hasLines -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = 16.dp), Alignment.Center) {
                Text(stringResource(R.string.blocks_none_drawn), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    textAlign = TextAlign.Center)
            }
            else -> {
                OverviewCard(state, viewModel, schoolName)
                Header(state, viewModel)
                Highlights(state, viewModel, actions)
                val g = state.guideView
                if (g == null) SkeletonList(count = 3) { SkeletonGradeRow() }
                else SchoolGuideSectors(g, state, viewModel, actions, anchors)
            }
        }
    }
}

// ── Mapa general, índice y PDF ───────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel) {
    val outline = MaterialTheme.colorScheme.outline
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, outline, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp)
                .guideTarget("detail_blocks.search"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                if (state.query.isEmpty()) Text(stringResource(R.string.sguide_search), fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    state.query, vm::setQuery, singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.query.isNotEmpty()) {
                Icon(Icons.Outlined.Close, stringResource(R.string.sguide_clear),
                    Modifier.size(18.dp).clip(CircleShape).clickable { vm.setQuery("") },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FaceMenu(state, vm)
            if (state.grades.isNotEmpty()) {
                ActiveChip(state.gradeOptions.filter { it in state.grades }.joinToString(" ")) { vm.clearGrades() }
            }
            if (state.betas.isNotEmpty()) {
                ActiveChip(BetaHeights.ALL.filter { it in state.betas }.map { betaLabel(it) }.joinToString(" ")) { vm.clearBetas() }
            }
            PillButton(stringResource(R.string.sguide_not_done), Icons.Outlined.RadioButtonUnchecked, state.onlyNotDone) { vm.toggleNotDone() }
            PillButton(stringResource(R.string.sguide_more), Icons.Outlined.Tune, state.filtersOpen,
                Modifier.guideTarget("detail_blocks.filters")) { vm.toggleFilters() }
        }
        if (state.filtersOpen) FilterPanel(state, vm)
        state.guideView?.let { g ->
            val lines = if (g.lineCount == 1) stringResource(R.string.sguide_count_lines_one) else stringResource(R.string.sguide_count_lines, g.lineCount)
            val blocks = if (g.blockCount == 1) stringResource(R.string.sguide_count_blocks_one) else stringResource(R.string.sguide_count_blocks, g.blockCount)
            val sectors = if (g.sectorCount == 1) stringResource(R.string.sguide_count_sectors_one) else stringResource(R.string.sguide_count_sectors, g.sectorCount)
            Text("$lines · $blocks · $sectors", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Cara de la piedra (N/E/S/O, varias): menú que no se cierra en cada toque. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FaceMenu(state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel) {
    var open by remember { mutableStateOf(false) }
    val label = if (state.faces.isEmpty()) stringResource(R.string.sguide_face)
    else stringResource(R.string.sguide_face_with, SchoolLinesExplorer.FACES.filter { it in state.faces }.joinToString(" "))
    PillButton(label, Icons.Outlined.Explore, state.faces.isNotEmpty()) { open = true }
    if (!open) return
    // Hoja como la del PDF (el desplegable de Material se veía anticuado): una tarjeta por cara, se pueden marcar varias.
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sguide_face), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground)
                if (state.faces.isNotEmpty()) {
                    Text(stringResource(R.string.sguide_clear), color = Terra, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.clip(CircleShape).clickable { vm.clearFaces() }.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            SchoolLinesExplorer.FACES.forEach { f ->
                val on = f in state.faces
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (on) Terra.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (on) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable { vm.toggleFace(f) }.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(f, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Terra)
                    Text(faceName(f), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground)
                    if (on) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Terra)
                }
            }
        }
    }
}

@Composable
private fun faceName(f: String): String = stringResource(
    when (f) { "N" -> R.string.sguide_north; "E" -> R.string.sguide_east; "S" -> R.string.sguide_south; else -> R.string.sguide_west }
)

@Composable
private fun PillButton(
    text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val fg = if (on) MaterialTheme.colorScheme.background else ink
    Row(
        modifier.clip(CircleShape)
            .background(if (on) ink else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) ink else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = fg)
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
    }
}

/** Filtro puesto desde "Más" que se quita con un toque. */
@Composable
private fun ActiveChip(text: String, onClear: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        Modifier.clip(CircleShape).background(ink).clickable(onClick = onClear).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.background, maxLines = 1)
        Icon(Icons.Outlined.Close, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.background)
    }
}

@Composable
private fun betaLabel(height: String): String =
    stringResource(if (height == BetaHeights.TALL) R.string.blocks_beta_tall else R.string.blocks_beta_short)

@Composable
private fun FilterPanel(state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelLabel(stringResource(R.string.blocks_filter_grade))
        ChipsRow {
            TextChip(stringResource(R.string.blocks_filter_all), on = state.grades.isEmpty()) { vm.clearGrades() }
            state.gradeOptions.forEach { g ->
                val on = g in state.grades
                GradePill(
                    g, large = true,
                    modifier = Modifier
                        .alpha(if (on) 1f else 0.5f)
                        .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                        .clickable { vm.toggleGrade(g) }
                )
            }
        }
        PanelLabel(stringResource(R.string.blocks_filter_beta))
        ChipsRow {
            BetaHeights.ALL.forEach { h -> TextChip(betaLabel(h), on = h in state.betas) { vm.toggleBeta(h) } }
        }
        if (state.betas.isNotEmpty()) {
            PanelLabel(stringResource(R.string.filter_beta_source))
            ChipsRow {
                BETA_SOURCES.forEach { (src, label) ->
                    TextChip(stringResource(label), on = state.betaSource == src) { vm.setBetaSource(src) }
                }
            }
        }
    }
}

@Composable
private fun PanelLabel(text: String) =
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold,
        fontSize = 9.sp, letterSpacing = 1.6.sp)

/** Fila de chips con scroll horizontal: con 12 grados distintos en una escuela grande no caben en una fila. */
@Composable
private fun ChipsRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun TextChip(text: String, on: Boolean, onClick: () -> Unit) {
    val fill = terraFillColor()
    Text(
        text,
        color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) fill else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) fill else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

// ── Destacadas (más repetidas / mejor valoradas), con los mismos filtros ────────────────────────────────────────

@Composable
private fun Highlights(state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel, actions: GuideActions) {
    val items = state.highlights ?: emptyList()
    Column(Modifier.padding(bottom = 14.dp).guideTarget("detail_blocks.podium"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.padding(horizontal = 16.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SchoolBlocksViewModel.Order.entries.forEach { o ->
                val on = state.order == o
                Text(
                    stringResource(if (o == SchoolBlocksViewModel.Order.Repeated) R.string.sguide_most_repeated else R.string.sguide_top_rated),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { vm.setOrder(o) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                stringResource(if (state.order == SchoolBlocksViewModel.Order.Repeated) R.string.sguide_none_repeated else R.string.sguide_none_rated),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { i, h -> HighlightCard(h, i + 1, state.order, actions) }
            }
        }
    }
}

@Composable
private fun HighlightCard(h: LineSearchHit, rank: Int, order: SchoolBlocksViewModel.Order, actions: GuideActions) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(156.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable { actions.onOpen(h.lineId, h.blockId) }
    ) {
        Box(Modifier.fillMaxWidth().height(104.dp)) {
            val photo = h.photoPath
            if (!photo.isNullOrBlank()) MiniTopoThumbnail(photoUrl = photo, linePath = h.linePath, grade = h.grade, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
            Text("$rank", color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(6.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                    .padding(top = 3.dp))
        }
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(h.lineName ?: h.blockName, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(h.sectorName ?: h.blockName, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradePill(h.grade, small = true, lineId = h.lineId)
                Spacer(Modifier.weight(1f))
                if (order == SchoolBlocksViewModel.Order.Repeated) {
                    Text("✓ ${h.repetitions ?: 0}", color = Ok, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                } else h.rating?.let { avg ->
                    Text("★ ${"%.1f".format(avg)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Grado en píldora rellena con SU color (el mismo de la app: verde, azul, morado, rojo…). Los grados ≤5c+ (blancos)
 * llevan texto oscuro y borde para no perderse sobre el papel. Nada si no hay grado.
 */
@Composable
fun GradePill(
    grade: String?, modifier: Modifier = Modifier, small: Boolean = false, large: Boolean = false,
    /** Si se da la vía, la pastilla pide su reparto por altura: si la altura parte la opinión, son dos colores. */
    lineId: String? = null
) {
    if (grade.isNullOrBlank()) return
    val split = rememberLineSplit(lineId)
    if (split != null) {
        Row(modifier.clip(CircleShape)) {
            listOf(split.tallGrade, split.shortGrade).forEach { g ->
                val (c, _, d) = gradeArgb(g)
                Text(
                    g, color = if (d) Color.Black.copy(alpha = 0.75f) else Color.White,
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = (if (large) 12 else if (small) 10 else 11).sp,
                    modifier = Modifier.background(Color(c.toInt()))
                        .padding(horizontal = if (large) 9.dp else 6.dp, vertical = if (large) 6.dp else 3.dp)
                )
            }
        }
        return
    }
    val (argb, _, dark) = gradeArgb(grade)
    Text(
        grade,
        color = if (dark) Color.Black.copy(alpha = 0.75f) else Color.White,
        fontFamily = Mono, fontWeight = FontWeight.Bold,
        fontSize = (if (large) 12 else if (small) 10 else 11).sp,
        modifier = modifier
            .clip(CircleShape)
            .background(Color(argb.toInt()))
            .then(if (dark) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier)
            .padding(horizontal = if (large) 11.dp else 8.dp, vertical = if (large) 6.dp else 3.dp)
    )
}
