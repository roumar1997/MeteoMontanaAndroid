package com.meteomontana.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.DrawOp
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.renderTopo
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import org.json.JSONArray

/** Modelo UI de una línea/vía. Usa Offset de Compose (solo para la capa Android). */
data class TopoLine(
    val name: String?,
    val grade: String?,
    val startType: String?,
    val points: List<Offset>
)

/** Convierte TopoLine (UI, con Offset) a TopoLineData (dominio, puro Kotlin). */
fun TopoLine.toLineData(strokeWidthPx: Float = 5f) = TopoLineData(
    name = name, grade = grade, startType = startType,
    points = points.map { it.x to it.y },
    strokeWidthPx = strokeWidthPx
)

/** Convierte el JSON `bloquesJson` (formato contribución BOULDER) a lista de TopoLine. */
fun parseBloquesJson(bloquesJson: String?): List<TopoLine> {
    if (bloquesJson.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(bloquesJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TopoLine(
                name = o.optString("name").takeIf { it.isNotEmpty() && it != "null" },
                grade = o.optString("grade").takeIf { it.isNotEmpty() && it != "null" },
                startType = o.optString("startType").takeIf { it.isNotEmpty() && it != "null" },
                points = parseLineStroke(o.optString("linePath")).points
            )
        }
    } catch (_: Throwable) { emptyList() }
}

/** Convierte la lista de `BlockLine` del backend a lista de TopoLine. */
fun List<BlockLine>.toTopoLines(): List<TopoLine> = map { line ->
    TopoLine(
        name = line.name,
        grade = line.grade,
        startType = line.startType,
        points = parseLineStroke(line.linePath).points
    )
}

/**
 * Foto con las líneas topo superpuestas. Aspect ratio 4:3 + Crop fijo.
 * El bloque Canvas traduce DrawOps producidos por renderTopo() — sin android.graphics.* en la lógica.
 */
@Composable
fun TopoPhotoCanvas(
    photoUrl: String,
    lines: List<TopoLine>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.Black)
    ) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Foto",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (lines.any { it.points.isNotEmpty() }) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ops = renderTopo(lines.map { it.toLineData() }, size.width, size.height)
                val nc = drawContext.canvas.nativeCanvas
                ops.forEach { op -> drawOp(op, nc) }
            }
        }
    }
}

/** Traduce un DrawOp a llamadas de DrawScope. Android-specific (stays in androidMain). */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOp(
    op: DrawOp,
    nc: android.graphics.Canvas
) {
    when (op) {
        is DrawOp.LinePath -> {
            val path = Path()
            op.pts.forEachIndexed { i, (x, y) ->
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val pathEffect = if (op.dashed) PathEffect.dashPathEffect(floatArrayOf(20f, 20f)) else null
            drawPath(path, color = Color(op.argb.toInt()), style = Stroke(width = op.widthPx, pathEffect = pathEffect))
        }
        is DrawOp.FilledCircle ->
            drawCircle(Color(op.argb.toInt()), radius = op.radius, center = Offset(op.cx, op.cy))
        is DrawOp.CircleStroke ->
            drawCircle(Color(op.argb.toInt()), radius = op.radius, center = Offset(op.cx, op.cy),
                style = Stroke(width = op.strokePx))
        is DrawOp.TextLabel ->
            nc.drawText(
                op.text, op.cx, op.cy + op.baselineOffsetPx,
                android.graphics.Paint().apply {
                    color = op.argb.toInt()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = op.sizePx
                    isFakeBoldText = op.bold
                }
            )
    }
}
