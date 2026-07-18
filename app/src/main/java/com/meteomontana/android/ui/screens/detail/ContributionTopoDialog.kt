package com.meteomontana.android.ui.screens.detail

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.renderTopo
import com.meteomontana.android.ui.components.drawOp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.gradeStyle

@Composable
fun ContributionTopoDialog(
    photoUri: Uri,
    bloques: List<BoulderBloqueForm>,
    onSave: (List<BoulderBloqueForm>) -> Unit,
    onDismiss: () -> Unit,
    existingLines: List<com.meteomontana.android.ui.components.TopoLine> = emptyList()
) {
    var selectedIdx by remember { mutableStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Una lista de puntos por bloque. SnapshotStateList para que el Canvas se redibuje en tiempo real.
    val lines = remember {
        mutableStateMapOf<Int, SnapshotStateList<Offset>>().also { map ->
            bloques.forEachIndexed { i, b ->
                map[i] = androidx.compose.runtime.mutableStateListOf<Offset>().also { list ->
                    list.addAll(b.linePath)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(Color.Black)
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Dibujar líneas",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif),
                    color = Terra,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                ) {
                    Text("✕", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // ── Selector de bloque ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    "Dibujando para:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    contentPadding = PaddingValues(end = Spacing.sm)
                ) {
                    itemsIndexed(bloques) { idx, b ->
                        val sel = idx == selectedIdx
                        val gStyle = gradeStyle(b.grade)
                        val bgColor = if (sel) gStyle.stroke else MaterialTheme.colorScheme.surface
                        val textColor = when {
                            !sel -> MaterialTheme.colorScheme.onSurface
                            gStyle.dark -> Color.Black
                            else -> Color.White
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(bgColor)
                                .border(1.dp, gStyle.stroke, RoundedCornerShape(2.dp))
                                .clickable { selectedIdx = idx }
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        ) {
                            Text(
                                "${b.name.ifBlank { "${idx + 1}" }} ${b.grade ?: ""}".trim(),
                                style = EyebrowTextStyle,
                                color = textColor
                            )
                        }
                    }
                    // Botón + NUEVO para añadir otro bloque en el futuro (deshabilitado aquí)
                    item {
                        Spacer(Modifier.width(Spacing.xs))
                    }
                }
            }

            // ── Foto + Canvas ────────────────────────────────────────────────────
            // El rectángulo de dibujo usa el aspect real de la foto (misma fórmula
            // topoAspectRatio que TopoPhotoCanvas) para que las coordenadas
            // normalizadas se vean idénticas en el admin y en los visores.
            var photoRatio by remember(photoUri) { mutableStateOf(4f / 3f) }
            // La foto ocupa el espacio disponible (weight) acotada por su alto, así
            // los botones del footer (GUARDAR, etc.) quedan SIEMPRE visibles incluso
            // con fotos verticales (antes la foto empujaba el footer fuera).
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(photoRatio)
                    .background(Color.Black)
                    .onSizeChanged { canvasSize = it }
            ) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { state ->
                        photoRatio = com.meteomontana.android.ui.components.topoAspectRatio(
                            state.result.drawable.intrinsicWidth,
                            state.result.drawable.intrinsicHeight
                        )
                    }
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        // MODO POR TOQUES: cada toque añade un punto a la línea
                        // (mucho más preciso que el dedo arrastrando). Convive
                        // con el trazado a mano: un arrastre REDIBUJA de cero.
                        .pointerInput(selectedIdx, canvasSize) {
                            detectTapGestures(onTap = { offset: Offset ->
                                if (canvasSize.width > 0 && canvasSize.height > 0) {
                                    val current = lines[selectedIdx] ?: return@detectTapGestures
                                    current.add(
                                        Offset(
                                        offset.x / canvasSize.width,
                                        offset.y / canvasSize.height
                                    ))
                                    // Imán inmediato del punto recién colocado.
                                    val others =
                                        existingLines.map { l -> l.points.map { it.x to it.y } } +
                                            lines.filterKeys { it != selectedIdx }
                                                .values.map { pts -> pts.map { it.x to it.y } }
                                    val snapped = com.meteomontana.android.domain.util.magnetizeStroke(
                                        current.map { it.x to it.y }, others)
                                    current.clear()
                                    snapped.forEach { (x, y) -> current.add(Offset(x, y)) }
                                }
                            })
                        }
                        .pointerInput(selectedIdx, canvasSize) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                                        val norm = Offset(
                                            offset.x / canvasSize.width,
                                            offset.y / canvasSize.height
                                        )
                                        lines[selectedIdx]?.clear()
                                        lines[selectedIdx]?.add(norm)
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                                        val norm = Offset(
                                            change.position.x / canvasSize.width,
                                            change.position.y / canvasSize.height
                                        )
                                        lines[selectedIdx]?.add(norm)
                                    }
                                },
                                onDragEnd = {
                                    // Al soltar: 1) SUAVIZADO (quita el temblor
                                    // del pulso, deja una línea limpia de guía) y
                                    // 2) IMÁN: los puntos cerca de otra vía se
                                    // ajustan a SUS vértices exactos → el tramo
                                    // común se pinta a franjas compartidas.
                                    val current = lines[selectedIdx]
                                    if (current != null && current.size >= 2) {
                                        val smooth = com.meteomontana.android.domain.util.simplifyStroke(
                                            current.map { it.x to it.y })
                                        val others =
                                            existingLines.map { l -> l.points.map { it.x to it.y } } +
                                                lines.filterKeys { it != selectedIdx }
                                                    .values.map { pts -> pts.map { it.x to it.y } }
                                        val snapped = com.meteomontana.android.domain.util.magnetizeStroke(
                                            smooth, others)
                                        current.clear()
                                        snapped.forEach { (x, y) -> current.add(Offset(x, y)) }
                                    }
                                }
                            )
                        }
                ) {
                    // Líneas existentes: se renderizan con su grado/color/badge/tipo
                    // para que el usuario vea exactamente dónde están las vías ya
                    // trazadas y no dibuje encima sin querer.
                    val existing = existingLines.map { line ->
                        TopoLineData(
                            name = line.name,
                            grade = line.grade,
                            startType = line.startType,
                            points = line.points.map { it.x to it.y },
                            strokeWidthPx = 5f
                        )
                    }
                    // Líneas nuevas (editables) — se numeran a continuación de las existentes.
                    val editorLines = lines.entries.sortedBy { it.key }.map { (idx, points) ->
                        val bloque = bloques.getOrNull(idx)
                        val strokeW = if (idx == selectedIdx) 8f else 5f
                        TopoLineData(
                            name = bloque?.name,
                            grade = bloque?.grade,
                            startType = bloque?.startType,
                            points = points.map { it.x to it.y },
                            strokeWidthPx = strokeW
                        )
                    }
                    val nc = drawContext.canvas.nativeCanvas
                    // density para que rayitas/franjas midan lo mismo que en la ficha.
                    val dens = drawContext.density.density
                    renderTopo(
                        existing + editorLines, size.width, size.height,
                        badgeR = 16f to 13f,
                        badgeTextPx = 26f to 9f,
                        startR = 26f to 22f,
                        startTextPx = 20f to 7f,
                        dashPx = 12f * dens to 9f * dens,
                        stripePx = 22f * dens
                    ).forEach { op -> drawOp(op, nc) }
                }
              }
            }

            // ── Hint ────────────────────────────────────────────────────────────
            Text(
                "Toca punto a punto para colocar la línea, o arrastra para trazarla a mano. Cerca de otra vía, el trazo se pega a ella (tramo compartido).",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // ── Footer ───────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // BORRAR LÍNEA (limpia la línea del bloque seleccionado)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                        .clickable { lines[selectedIdx]?.clear() }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕ BORRAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.error)
                }

                // CANCELAR
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CANCELAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                }

                // GUARDAR LÍNEAS
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onBackground)
                        .clickable {
                            val updated = bloques.mapIndexed { idx, b ->
                                b.copy(linePath = lines[idx]?.toList() ?: emptyList())
                            }
                            onSave(updated)
                        }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("GUARDAR\nLÍNEAS", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.background,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}


