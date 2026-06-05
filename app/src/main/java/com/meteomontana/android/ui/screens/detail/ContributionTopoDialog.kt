package com.meteomontana.android.ui.screens.detail

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
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
    onDismiss: () -> Unit
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
            // Aspect ratio 4:3 + Crop fija el rectángulo de dibujo para que las
            // coordenadas normalizadas se vean idénticas en el admin.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(Color.Black)
                    .onSizeChanged { canvasSize = it }
            ) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
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
                                onDragEnd = {}
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    lines.forEach { (idx, points) ->
                        if (points.isEmpty()) return@forEach
                        val bloque = bloques.getOrNull(idx) ?: return@forEach
                        val style = gradeStyle(bloque.grade)
                        val isSelected = idx == selectedIdx

                        // Línea
                        val path = Path()
                        points.forEachIndexed { i, p ->
                            val x = p.x * w
                            val y = p.y * h
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        val strokeW = if (isSelected) 8f else 5f
                        val pathEffect = if (style.dashed)
                            PathEffect.dashPathEffect(floatArrayOf(20f, 20f)) else null
                        // Outline oscuro para que líneas blancas (≤5c+) sean visibles
                        if (style.dark) {
                            drawPath(path, color = Color(0xCC000000),
                                style = Stroke(width = strokeW + 5f, pathEffect = pathEffect))
                        }
                        drawPath(path, color = style.stroke,
                            style = Stroke(width = strokeW, pathEffect = pathEffect))

                        // Badge numérico en el punto de inicio (con halo blanco)
                        val first = points.first()
                        val fx = first.x * w
                        val fy = first.y * h
                        drawCircle(Color.White, radius = 16f, center = Offset(fx, fy))
                        drawCircle(style.stroke, radius = 13f, center = Offset(fx, fy))
                        nativeCanvas.drawText(
                            "${idx + 1}",
                            fx, fy + 9f,
                            android.graphics.Paint().apply {
                                color = if (style.dark) android.graphics.Color.BLACK
                                        else android.graphics.Color.WHITE
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = 26f
                                isFakeBoldText = true
                            }
                        )

                        // Icono de tipo de inicio en el punto final
                        val last = points.last()
                        val lx = last.x * w
                        val ly = last.y * h
                        drawStartIcon(bloque.startType, Offset(lx, ly), style.stroke, style.dark, nativeCanvas)
                    }
                }
            }

            // Spacer flexible para empujar hint + footer al fondo
            Spacer(modifier = Modifier.weight(1f))

            // ── Hint ────────────────────────────────────────────────────────────
            Text(
                "Arrastra sobre la foto para trazar la línea del bloque seleccionado",
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

/**
 * Badge circular del tipo de inicio: círculo blanco + relleno de color + letra.
 * Siempre visible sobre cualquier fondo de foto.
 * PIE=▲  SIT=⊥  LANCE=↑  TRAV=↔
 */
private fun DrawScope.drawStartIcon(
    type: String?,
    center: Offset,
    color: Color,
    isDark: Boolean,
    nativeCanvas: android.graphics.Canvas
) {
    if (type == null) return

    // Acepta tanto los valores de app (PIE/SIT/LANCE/TRAV) como los del
    // backend al re-cargar un bloque existente (STAND/SIT/JUMP/TRAV).
    val label = when (type.uppercase()) {
        "PIE", "STAND"   -> "PIE"
        "SIT"            -> "SIT"
        "LANCE", "JUMP"  -> "LAN"
        "TRAV"           -> "TRV"
        else             -> return
    }

    // Halo exterior: negro si el grado es blanco (para contraste), blanco en el resto
    val haloColor = if (isDark) Color(0xFF000000) else Color.White
    drawCircle(haloColor, radius = 26f, center = center)
    drawCircle(color, radius = 22f, center = center)
    // Si el círculo de color es blanco, añadimos borde negro fino para que se distinga
    if (isDark) {
        drawCircle(Color(0xFF000000), radius = 22f, center = center,
            style = Stroke(width = 2f))
    }

    // Texto: negro si grado blanco, blanco en otros
    nativeCanvas.drawText(
        label,
        center.x,
        center.y + 7f,
        android.graphics.Paint().apply {
            this.color = if (isDark) android.graphics.Color.BLACK
                         else android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 20f
            isFakeBoldText = true
        }
    )
}

