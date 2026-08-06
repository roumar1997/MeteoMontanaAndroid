package com.meteomontana.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.domain.util.renderTopo

/**
 * Foto de topo para MIRAR una piedra: se puede ampliar y, al tocar una vía,
 * las demás se apagan.
 *
 * Es la respuesta a las piedras con muchas vías — el muro de Teverga tiene 13
 * sobre la misma foto, y a tamaño de móvil son indistinguibles.
 *
 * **Solo se usa donde miras UNA piedra** (ficha de piedra, revisión del admin).
 * En el feed, en el buscador o en las miniaturas se sigue usando
 * [TopoPhotoCanvas], que no lleva gestos: ahí la foto vive dentro de una lista
 * que se desplaza, y el pellizco pelearía con el scroll. En esos sitios lo
 * correcto es que tocar abra la piedra, y ampliar sea cosa de esta pantalla.
 */
@Composable
fun TopoPhotoViewer(
    photoUrl: String,
    lines: List<TopoLine>,
    modifier: Modifier = Modifier,
    /** Índice de la vía resaltada desde fuera (p. ej. al tocarla en la lista). */
    focusedIndex: Int? = null,
    onFocusChange: (Int?) -> Unit = {}
) {
    var ratio by remember(photoUrl) { mutableStateOf(4f / 3f) }
    // El foco ya NO se activa tocando la foto (a Rodrigo no le convencia): solo
    // lo enciende quien llame desde fuera, p.ej. tocando la via en la lista.
    val focus = focusedIndex

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.Black)
    ) {
        TopoZoomBox(
            modifier = Modifier.fillMaxSize(),
            editable = false
        ) { camera ->
            AsyncImage(
                model = photoUrl,
                contentDescription = "Foto de la piedra",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    ratio = topoAspectRatio(
                        state.result.drawable.intrinsicWidth,
                        state.result.drawable.intrinsicHeight
                    )
                }
            )
            if (lines.any { it.points.isNotEmpty() }) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Los tamaños se dividen por la escala: al ampliar, el trazo
                    // debe seguir midiendo lo mismo en pantalla, no engordar con
                    // la foto hasta taparla.
                    val d = density
                    val z = camera.strokeFactor()
                    val datos = lines.mapIndexed { i, l ->
                        l.toLineData().copy(
                            strokeWidthPx = 3.5f * d * z,
                            muted = focus != null && focus != i
                        )
                    }
                    val ops = renderTopo(
                        datos, size.width, size.height,
                        badgeR = 9f * d * z to 7f * d * z,
                        badgeTextPx = 10f * d * z to 3.5f * d * z,
                        startR = 10.5f * d * z to 8.5f * d * z,
                        startTextPx = 7f * d * z to 2.5f * d * z,
                        dashPx = 12f * d * z to 9f * d * z,
                        stripePx = 22f * d * z,
                        // SIN el zoom: si el abanico encoge al ampliar, los
                        // numeros se van juntando y parece que la linea se
                        // desliza sobre la roca.
                        fanSpacingPx = (9f * d * 2f + 4f) to (10.5f * d * 2f + 4f)
                    )
                    val nc = drawContext.canvas.nativeCanvas
                    ops.forEach { op -> drawOp(op, nc) }
                }
            }
        }
    }
}
