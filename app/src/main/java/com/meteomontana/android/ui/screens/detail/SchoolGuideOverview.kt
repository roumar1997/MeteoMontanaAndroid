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
import kotlinx.coroutines.delay

// Cabecera de la guía: mapa general de la escuela (con su pantalla completa) y el botón del PDF.

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun OverviewCard(state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel, schoolName: String) {
    var fullScreen by remember { mutableStateOf(false) }
    val outline = MaterialTheme.colorScheme.outline
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val ov = state.overview
        // El mapa sale PLEGADO: una barra que lo despliega (ocupa mucho y casi siempre se va a la lista).
        if (ov != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, outline, RoundedCornerShape(12.dp))
                    .clickable { vm.toggleMap() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Map, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.sguide_school_map), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Icon(if (state.mapExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null,
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
            if (state.mapExpanded) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { fullScreen = true }) {
                    OverviewCanvas(ov, labelScale = 1f) { vm.jumpToSectorNamed(it) }
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)).clickable { fullScreen = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Outlined.OpenInFull, null, Modifier.size(12.dp), tint = Color.White)
                        Text(stringResource(R.string.sguide_expand), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        // Índice, como la portada del PDF: tocar un sector = bajar hasta él (y desplegarlo).
        val g = state.guideView
        if (g != null && g.sectors.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.sguide_index), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.sectors.forEach { s ->
                        Row(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, outline, CircleShape).clickable { vm.jumpToSector(s.key) }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(sectorTitle(s), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.lineCount}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        PdfButton(state, vm, schoolName)
    }
    if (fullScreen && state.overview != null) {
        OverviewFullScreen(state.overview, onDismiss = { fullScreen = false }) { name ->
            fullScreen = false
            vm.jumpToSectorNamed(name)
        }
    }
}

/** Botón "Descargar guía en PDF": toda la escuela o solo un sector. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun PdfButton(state: SchoolBlocksViewModel.UiState, vm: SchoolBlocksViewModel, schoolName: String) {
    var menu by remember { mutableStateOf(false) }
    val g = state.guideView
    Box {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(enabled = !state.makingPdf && g != null) {
                    if (g != null && g.sectors.size > 1) menu = true else vm.exportPdf(schoolName, null)
                }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.makingPdf) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onBackground)
            Text(
                stringResource(if (state.makingPdf) R.string.sguide_making_pdf else R.string.sguide_download_pdf),
                fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (menu && g != null) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { menu = false },
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.sguide_download_pdf), fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground)
                    PdfChoice(Icons.Outlined.MenuBook, stringResource(R.string.sguide_whole_school), null) {
                        menu = false; vm.exportPdf(schoolName, null)
                    }
                    Text(stringResource(R.string.sguide_one_sector).uppercase(), style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    g.sectors.forEach { s ->
                        PdfChoice(Icons.Outlined.PictureAsPdf, sectorTitle(s), s.lineCount) {
                            menu = false; vm.exportPdf(schoolName, s.key)
                        }
                    }
                }
            }
        }
    }
}

/** Una opción de la hoja del PDF: tarjeta blanca con icono, nombre y (en los sectores) cuántas vías lleva. */
@Composable
private fun PdfChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, count: Int?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Terra)
        Text(title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        count?.let { Text("$it", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Terra) }
    }
}

/** La imagen del mapa con los sectores encima como botones (en la tarjeta y a pantalla completa). */
@Composable
private fun OverviewCanvas(ov: GuideOverview, labelScale: Float, onSector: (String) -> Unit) {
    val img = remember(ov.image) { ov.image.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ov.image.width.toFloat() / ov.image.height.coerceAtLeast(1))) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        ov.sectors.forEach { s ->
            var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            Text(
                s.name.uppercase(),
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = (8.5f * labelScale).sp, letterSpacing = 0.4.sp,
                color = Color(0xFF1C1C1A), maxLines = 1,
                modifier = Modifier
                    .offset { IntOffset((s.x * wPx - size.width / 2).roundToInt(), (s.y * hPx - size.height / 2).roundToInt()) }
                    .onSizeChanged { size = it }
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.85f))
                    .clickable { onSector(s.name) }
                    .padding(horizontal = (5 * labelScale).dp, vertical = (2.5f * labelScale).dp)
            )
        }
    }
}

/** El mapa general a pantalla completa: pellizcar para acercar, arrastrar, tocar un sector para ir a él. */
@Composable
private fun OverviewFullScreen(ov: GuideOverview, onDismiss: () -> Unit, onSector: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableStateOf(1f) }
        var dx by remember { mutableStateOf(0f) }
        var dy by remember { mutableStateOf(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale > 1f) { dx += pan.x; dy += pan.y } else { dx = 0f; dy = 0f }
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = dx, translationY = dy),
                contentAlignment = Alignment.Center
            ) { OverviewCanvas(ov, labelScale = 1f / scale, onSector = onSector) }
            Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.common_close),
                    Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onDismiss).padding(8.dp), tint = Color.White)
                Text(stringResource(R.string.sguide_tap_sector), color = Color.White, fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

// ── Cabecera única: los filtros valen para las destacadas y la guía ─────────────────────────────────────────────

