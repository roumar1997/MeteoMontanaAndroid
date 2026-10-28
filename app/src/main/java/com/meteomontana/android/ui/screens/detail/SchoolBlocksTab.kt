package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonGradeRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.meteomontana.android.ui.components.rememberLineSplit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.schools.BetaHeights
import com.meteomontana.android.domain.usecase.schools.SectorGroup
import com.meteomontana.android.domain.util.gradeArgb
import com.meteomontana.android.ui.guide.guideTarget
import com.meteomontana.android.ui.screens.schools.MiniTopoThumbnail
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Pestaña BLOQUES/VÍAS de la ficha de una escuela: las vías más repetidas de la escuela, filtrables por uno o varios
 * grados y por "tiene beta" (+1,70 / −1,70), con buscador por nombre y el resto agrupado por sector. Espejo de
 * `SchoolBlocksExplorerView` (iOS).
 *
 * "Repetida" = personas distintas que la tienen como HECHA en su diario. El servidor devuelve solo el total, nunca
 * quién la ha hecho. Esta vista SOLO pinta: el estado vive en [SchoolBlocksViewModel] y los filtros, el orden, el podio
 * y los sectores los calcula `SchoolLinesExplorer` (shared).
 *
 * [onOpen] = toca una vía: abre su piedra (recibe el id de la piedra).
 */
@Composable
fun SchoolBlocksTab(viewModel: SchoolBlocksViewModel, onOpen: (blockId: String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

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
                BlocksFilters(state, viewModel)
                BlocksResults(state, viewModel, onOpen)
            }
        }
    }
}

// ── Filtros ───────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlocksFilters(state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().guideTarget("detail_blocks.search"),
            placeholder = { Text(stringResource(R.string.blocks_search_hint), fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(4.dp)
        )
        FilterToggle(state, vm)
        if (state.filtersOpen) FilterPanel(state, vm)
    }
}

@Composable
private fun FilterToggle(state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel) {
    val active = state.activeCount > 0
    val fill = terraFillColor()
    val orderLabel = stringResource(
        if (state.order == SchoolBlocksViewModel.Order.Repeated) R.string.blocks_order_repeated else R.string.blocks_order_rated
    )
    val betaLabels = BetaHeights.ALL.filter { it in state.betas }.map { betaLabel(it) }
    val summary = buildList {
        if (state.grades.isNotEmpty()) add(state.gradeOptions.filter { it in state.grades }.joinToString(" "))
        if (state.betas.isNotEmpty()) add(betaLabels.joinToString(" "))
        add(orderLabel.lowercase().replaceFirstChar { it.uppercase() })
    }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable { vm.toggleFilters() }.guideTarget("detail_blocks.filters"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (active) fill else MaterialTheme.colorScheme.surface)
                .border(1.dp, if (active) fill else MaterialTheme.colorScheme.outline, CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val fg = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(Icons.Outlined.Tune, null, tint = fg, modifier = Modifier.size(14.dp))
            Text(
                if (active) stringResource(R.string.schools_filters_with_count, state.activeCount) else stringResource(R.string.common_filters),
                style = EyebrowTextStyle.copy(fontSize = 11.sp, letterSpacing = 1.sp), color = fg
            )
        }
        Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(
            if (state.filtersOpen) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
        )
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
        PanelLabel(stringResource(R.string.blocks_filter_sort))
        ChipsRow {
            SchoolBlocksViewModel.Order.entries.forEach { o ->
                TextChip(
                    stringResource(if (o == SchoolBlocksViewModel.Order.Repeated) R.string.blocks_order_repeated else R.string.blocks_order_rated),
                    on = state.order == o
                ) { vm.setOrder(o) }
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

// ── Resultados ────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlocksResults(state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel, onOpen: (String) -> Unit) {
    val view = remember(state) { state.view }
    if (view == null) {
        SkeletonList(count = 3) { SkeletonGradeRow() }
        return
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (view.total == 1) stringResource(R.string.blocks_count_one) else stringResource(R.string.blocks_count_other, view.total),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, letterSpacing = 1.8.sp, modifier = Modifier.weight(1f)
        )
        Text(stringResource(R.string.blocks_repeated_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
    }
    if (view.total == 0) {
        Text(stringResource(R.string.blocks_none_match), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
    }
    if (view.podium.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp).guideTarget("detail_blocks.podium"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            view.podium.forEach { h -> PodiumCard(h, Modifier.weight(1f), onOpen) }
            // Si hay menos de 3, se rellenan los huecos para que las tarjetas no se estiren.
            repeat(maxOf(0, 3 - view.podium.size)) { Spacer(Modifier.weight(1f)) }
        }
    }
    view.groups.forEach { g -> SectorBlock(g, state, vm, onOpen) }
}

@Composable
private fun SectorBlock(g: SectorGroup, state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel, onOpen: (String) -> Unit) {
    val key = g.sector ?: ""
    val open = key in state.openSectors
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .clickable { vm.toggleSector(key) }.padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(g.sector ?: stringResource(R.string.blocks_no_sector), color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        val ui = com.meteomontana.android.ui.components.LocalSchoolRestrictions.current
        val sectorId = ui?.blocks?.firstOrNull { it.type == "ZONE" && it.name == g.sector }?.id
        if (ui != null && sectorId != null) {
            com.meteomontana.android.ui.components.RestrictionMarkButton(ui.allInSector(sectorId))
            Spacer(Modifier.size(8.dp))
        }
        Text("${g.hits.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.size(6.dp))
        Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
    if (open) g.hits.forEach { h -> LineRow(h, onOpen) }
}

@Composable
private fun Thumbnail(h: LineSearchHit, modifier: Modifier) {
    val photo = h.photoPath
    if (!photo.isNullOrBlank()) MiniTopoThumbnail(photoUrl = photo, linePath = h.linePath, grade = h.grade, modifier = modifier)
    else Box(modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
}

@Composable
private fun PodiumCard(h: LineSearchHit, modifier: Modifier, onOpen: (String) -> Unit) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .clickable { onOpen(h.blockId) }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Thumbnail(h, Modifier.fillMaxWidth().height(66.dp))
        Text(h.lineName ?: h.blockName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        h.sectorName?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✓ ${h.repetitions ?: 0}", color = Ok, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            GradePill(h.grade, small = true, lineId = h.lineId)
        }
    }
}

@Composable
private fun LineRow(h: LineSearchHit, onOpen: (String) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(h.blockId) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Thumbnail(h, Modifier.size(46.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(h.lineName ?: h.blockName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                GradePill(h.grade, lineId = h.lineId)
                com.meteomontana.android.ui.components.LocalSchoolRestrictions.current?.let { ui ->
                    com.meteomontana.android.ui.components.RestrictionMarkButton(ui.forBlockId(h.blockId))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val reps = h.repetitions ?: 0
                if (reps > 0) Text("✓ $reps", color = Ok, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                val avg = h.rating
                if (avg != null && (h.ratingCount ?: 0) > 0) {
                    Text("★ ${"%.1f".format(avg)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
                }
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
