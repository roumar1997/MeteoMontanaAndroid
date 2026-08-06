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
import androidx.compose.runtime.mutableStateListOf
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

/**
 * Todas las vias con las que el trazo puede compartir tramo: las que ya existen
 * en la piedra y las demas que se estan dibujando ahora.
 */
private fun otrasVias(
    existingLines: List<com.meteomontana.android.ui.components.TopoLine>,
    lines: Map<Int, SnapshotStateList<Offset>>,
    selectedIdx: Int
): List<List<Pair<Float, Float>>> =
    (existingLines.map { l -> l.points.map { it.x to it.y } } +
        lines.filterKeys { it != selectedIdx }.values.map { pts -> pts.map { it.x to it.y } })
        // Solo vias TRAZADAS. Una via con un unico punto suelto actuaba como
        // iman y se llevaba el trazo a donde no era: es lo que hacia que unirse
        // a mitad de otra via acabase pegado a su inicio. iOS ya lo filtraba.
        .filter { it.size >= 2 }

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
    // Lo que habia en la via antes de empezar este trazo, para restaurarlo si
    // el gesto acaba siendo un pellizco.
    var lineBeforeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // Vertice agarrado para corregirlo. Sin esto, arreglar un punto torcido
    // obliga a volver a trazar la via entera.
    var draggingVertex by remember { mutableStateOf<Int?>(null) }

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

    // Historial para DESHACER: (indice de via, como estaba). Se apila ANTES de
    // cada cambio. Sin esto, mover una via sin querer al revisar la propuesta
    // de otro no tiene vuelta atras.
    val historial = remember { mutableStateListOf<Pair<Int, List<Offset>>>() }
    // Radio del iman en coordenadas de foto, ajustado a la ampliacion actual.
    var imanRadio by remember { mutableStateOf(0.04f) }
    fun apunta() {
        lines[selectedIdx]?.let { historial.add(selectedIdx to it.toList()) }
        if (historial.size > 40) historial.removeAt(0)
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

                // -- Gestos y zoom -------------------------------------
                // Todo el reparto de dedos vive en TopoZoomBox (un dedo dibuja,
                // dos amplian y mueven, doble toque acerca). Aqui solo queda lo
                // que SIGNIFICA cada gesto para el editor.
                com.meteomontana.android.ui.components.TopoZoomBox(
                    modifier = Modifier.fillMaxSize(),
                    editable = true,
                    onStrokeStart = { px, py ->
                        val current = lines[selectedIdx]
                        if (current != null) {
                            // Copia de seguridad: si entra un segundo dedo a
                            // mitad del trazo se restaura lo que habia, en vez
                            // de dejar la via a medio borrar.
                            lineBeforeStroke = current.toList()
                            apunta()
                            // Si el dedo cae encima de un vertice existente, se
                            // AGARRA ese punto para corregirlo. Solo si la via
                            // ya esta trazada: con dos puntos aun se esta
                            // dibujando y agarrar estorbaria.
                            val v = if (current.size >= 3)
                                com.meteomontana.android.domain.util.nearestVertexIndex(
                                    current.map { it.x to it.y }, px, py)
                            else null
                            draggingVertex = v
                            if (v == null) { current.clear(); current.add(Offset(px, py)) }
                            else current[v] = Offset(px, py)
                        }
                    },
                    onStrokePoint = { px, py ->
                        val current = lines[selectedIdx]
                        val v = draggingVertex
                        if (current != null) {
                            if (v != null && v < current.size) current[v] = Offset(px, py)
                            else {
                                // Solo si el dedo se ha movido de verdad: con el
                                // dedo casi quieto llegaban decenas de puntos
                                // identicos por segundo, y el limpiador de
                                // pasadas superpuestas los tomaba por un
                                // retrazado y dejaba la linea SIN PINTAR hasta
                                // soltar. Era el "no veo lo que dibujo".
                                val ult = current.lastOrNull()
                                val lejos = ult == null ||
                                    kotlin.math.abs(px - ult.x) + kotlin.math.abs(py - ult.y) > 0.003f
                                if (lejos) current.add(Offset(px, py))
                            }
                        }
                    },
                    onStrokeCancel = {
                        val current = lines[selectedIdx]
                        if (current != null) {
                            current.clear(); current.addAll(lineBeforeStroke)
                        }
                        draggingVertex = null
                    },
                    onStrokeEnd = {
                        val current = lines[selectedIdx]
                        val corrigiendo = draggingVertex != null
                        draggingVertex = null
                        if (current != null && current.size >= 2) {
                            // Corrigiendo un vertice NO se suaviza: el suavizado
                            // borra puntos, y el que acabas de colocar a mano es
                            // justo el que quieres conservar. El iman si se
                            // aplica, para no romper un tramo compartido.
                            val base = if (corrigiendo) current.map { it.x to it.y }
                            else com.meteomontana.android.domain.util.simplifyStroke(
                                current.map { it.x to it.y })
                            val snapped = com.meteomontana.android.domain.util.magnetizeStroke(
                                base, otrasVias(existingLines, lines, selectedIdx))
                            current.clear()
                            snapped.forEach { (x, y) -> current.add(Offset(x, y)) }
                        }
                    },
                    onTap = { px, py ->
                        // Punto a punto: mas preciso que el dedo a mano, y con
                        // el zoom se vuelve preciso de verdad.
                        val current = lines[selectedIdx]
                        if (current != null) {
                            current.add(Offset(px, py))
                            val snapped = com.meteomontana.android.domain.util.magnetizeStroke(
                                current.map { it.x to it.y },
                                otrasVias(existingLines, lines, selectedIdx), imanRadio)
                            current.clear()
                            snapped.forEach { (x, y) -> current.add(Offset(x, y)) }
                        }
                    }
                ) { camera ->
                    // El radio del iman se divide por la ampliacion: asi agarra
                    // el mismo trozo de PANTALLA con la foto entera y ampliada.
                    imanRadio = 0.04f * camera.strokeFactor()
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
                    Canvas(modifier = Modifier.fillMaxSize()) {
                    // Al ampliar, todo se dibuja dentro de una capa escalada:
                    // sin dividir por la escala, al 400% el trazo y los badges
                    // engordarian hasta tapar la roca que ibas a mirar.
                    val z = camera.strokeFactor()
                    val existing = existingLines.map { line ->
                        TopoLineData(
                            name = line.name,
                            grade = line.grade,
                            startType = line.startType,
                            points = line.points.map { it.x to it.y },
                            strokeWidthPx = 5f * z
                        )
                    }
                    val editorLines = lines.entries.sortedBy { it.key }.map { (idx, points) ->
                        val bloque = bloques.getOrNull(idx)
                        val strokeW = if (idx == selectedIdx) 8f else 5f
                        TopoLineData(
                            name = bloque?.name,
                            grade = bloque?.grade,
                            startType = bloque?.startType,
                            points = points.map { it.x to it.y },
                            strokeWidthPx = strokeW * z
                        )
                    }
                    val nc = drawContext.canvas.nativeCanvas
                    // density para que rayitas/franjas midan lo mismo que en la ficha.
                    val dens = drawContext.density.density
                    renderTopo(
                        existing + editorLines, size.width, size.height,
                        badgeR = 16f * z to 13f * z,
                        badgeTextPx = 26f * z to 9f * z,
                        startR = 26f * z to 22f * z,
                        startTextPx = 20f * z to 7f * z,
                        dashPx = 12f * dens * z to 9f * dens * z,
                        stripePx = 22f * dens * z,
                        // Sin escalar, para que los badges no se junten al ampliar.
                        fanSpacingPx = (16f * 2f + 4f) to (26f * 2f + 4f)
                    ).forEach { op -> drawOp(op, nc) }
                    // Vertices de la via seleccionada: si no se ven, nadie
                    // adivina que se pueden arrastrar.
                    lines[selectedIdx]?.forEach { pt ->
                        drawCircle(
                            androidx.compose.ui.graphics.Color.White,
                            radius = 5f * z,
                            center = Offset(pt.x * size.width, pt.y * size.height)
                        )
                        drawCircle(
                            androidx.compose.ui.graphics.Color.Black,
                            radius = 5f * z,
                            center = Offset(pt.x * size.width, pt.y * size.height),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * z)
                        )
                    }
                    }
                }
              }
            }

            // ── Hint ────────────────────────────────────────────────────────────
            // Ayuda + controles de vista. La lupa es lo unico que no se puede
            // juzgar sin el movil en la mano: a unos les salva y a otros les
            // tapa media foto. Interruptor, y que decida quien dibuja.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                val hayQueDeshacer = historial.isNotEmpty()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(
                            1.dp,
                            if (hayQueDeshacer) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(2.dp)
                        )
                        .clickable(enabled = hayQueDeshacer) {
                            val (idx, antes) = historial.removeAt(historial.size - 1)
                            lines[idx]?.let { l -> l.clear(); l.addAll(antes) }
                            selectedIdx = idx
                        }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text(
                        "DESHACER",
                        style = EyebrowTextStyle,
                        color = if (hayQueDeshacer) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Un dedo dibuja · dos amplían y mueven · doble toque acerca",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
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


