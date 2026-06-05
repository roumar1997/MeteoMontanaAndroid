package com.meteomontana.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.meteomontana.android.data.api.dto.BlockLineDto
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.ui.theme.gradeStyle
import org.json.JSONArray

/** Modelo común para una línea/vía dibujada sobre la foto. */
data class TopoLine(
    val name: String?,
    val grade: String?,
    val startType: String?,
    val points: List<Offset>
)

/** Convierte el JSON `bloquesJson` (formato de contribución BOULDER) a lista de TopoLine. */
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

/** Convierte la lista de `BlockLineDto` del backend a lista de TopoLine. */
fun List<BlockLineDto>.toTopoLines(): List<TopoLine> = map { line ->
    TopoLine(
        name = line.name,
        grade = line.grade,
        startType = line.startType,
        points = parseLineStroke(line.linePath).points
    )
}

/**
 * Foto con las líneas topo superpuestas. Mismo render que el editor de propuestas para
 * que el admin y el usuario vean exactamente lo mismo.
 *
 * Aspect ratio 4:3 + Crop fijo: las coordenadas normalizadas mapean al mismo rectángulo
 * en cualquier sitio donde se use este composable.
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
                val w = size.width
                val h = size.height
                val nc = drawContext.canvas.nativeCanvas

                lines.forEachIndexed { idx, line ->
                    if (line.points.isEmpty()) return@forEachIndexed
                    val style = gradeStyle(line.grade)

                    val path = Path()
                    line.points.forEachIndexed { i, p ->
                        val x = p.x * w
                        val y = p.y * h
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val pathEffect = if (style.dashed)
                        PathEffect.dashPathEffect(floatArrayOf(20f, 20f)) else null
                    if (style.dark) {
                        drawPath(path, color = Color(0xCC000000),
                            style = Stroke(width = 9f, pathEffect = pathEffect))
                    }
                    drawPath(path, color = style.stroke,
                        style = Stroke(width = 5f, pathEffect = pathEffect))

                    // Badge numérico en el punto de inicio
                    val first = line.points.first()
                    val fx = first.x * w; val fy = first.y * h
                    drawCircle(Color.White, radius = 14f, center = Offset(fx, fy))
                    drawCircle(style.stroke, radius = 11f, center = Offset(fx, fy))
                    nc.drawText(
                        "${idx + 1}", fx, fy + 7f,
                        android.graphics.Paint().apply {
                            color = if (style.dark) android.graphics.Color.BLACK
                                    else android.graphics.Color.WHITE
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 22f; isFakeBoldText = true
                        }
                    )

                    // Badge de tipo de inicio en el punto final
                    val label = when (line.startType?.uppercase()) {
                        "PIE", "STAND" -> "PIE"
                        "SIT"          -> "SIT"
                        "LANCE", "JUMP" -> "LAN"
                        "TRAV"         -> "TRV"
                        else           -> null
                    }
                    if (label != null) {
                        val last = line.points.last()
                        val lx = last.x * w; val ly = last.y * h
                        val haloColor = if (style.dark) Color(0xFF000000) else Color.White
                        drawCircle(haloColor, radius = 22f, center = Offset(lx, ly))
                        drawCircle(style.stroke, radius = 18f, center = Offset(lx, ly))
                        if (style.dark) {
                            drawCircle(Color(0xFF000000), radius = 18f,
                                center = Offset(lx, ly), style = Stroke(width = 2f))
                        }
                        nc.drawText(
                            label, lx, ly + 6f,
                            android.graphics.Paint().apply {
                                color = if (style.dark) android.graphics.Color.BLACK
                                        else android.graphics.Color.WHITE
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = 18f; isFakeBoldText = true
                            }
                        )
                    }
                }
            }
        }
    }
}
