package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.meteomontana.android.domain.util.TopoCamera
import kotlin.math.roundToInt

/**
 * Lienzo de topo con zoom: coloca la foto y sus líneas, reparte los gestos y
 * ofrece la lupa. Es la pieza que usan por igual el editor y el visor.
 *
 * **No decide nada**: no sabe qué significa un trazo ni qué hacer al terminarlo.
 * Solo traduce dedos a gestos y avisa. Quien decide es el llamante, que además
 * es quien tiene el estado de las vías. Un componente tonto es un componente
 * reutilizable, y aquí lo reutilizan seis pantallas.
 *
 * **Reparto de gestos**, sin adivinar nada — se cuenta cuántos dedos hay:
 * - un dedo arrastrando → dibuja ([onStrokeStart] / [onStrokePoint] / [onStrokeEnd]);
 * - un toque suelto → añade un punto ([onTap]);
 * - **dos dedos → ampliar y mover**, y el trazo en curso se CANCELA. Sin esa
 *   cancelación, cada vez que fueras a ampliar quedaría una rayita basura;
 * - doble toque → ampliar ahí / volver a la vista completa.
 *
 * Todo lo que sale de aquí va en **coordenadas de la foto (0..1)**, nunca en
 * píxeles: es lo que permite dibujar medio trazo ampliado y el resto alejado.
 */
@Composable
fun TopoZoomBox(
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onStrokeStart: (Float, Float) -> Unit = { _, _ -> },
    onStrokePoint: (Float, Float) -> Unit = { _, _ -> },
    onStrokeEnd: () -> Unit = {},
    onStrokeCancel: () -> Unit = {},
    onTap: (Float, Float) -> Unit = { _, _ -> },
    onCameraChange: (TopoCamera) -> Unit = {},
    content: @Composable BoxScope.(TopoCamera) -> Unit
) {
    var camera by remember { mutableStateOf(TopoCamera.NONE) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    fun setCamera(c: TopoCamera) {
        camera = c
        onCameraChange(c)
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewSize = it }
            .background(Color.Black)
            // Sin recortar, la foto ampliada se sale del marco y tapa lo de
            // alrededor: parecia que al ampliar desaparecian las vias de arriba.
            .clipToBounds()
            .pointerInput(editable, viewSize) {
                if (viewSize.width == 0) return@pointerInput
                val w = viewSize.width.toFloat()
                val h = viewSize.height.toFloat()
                val toqueMaxPx = 12f * density        // por debajo de esto, es un toque

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dibujando = false
                    var multiTouch = false
                    var movido = 0f
                    var ultimo = down.position

                    if (editable) {
                        val (px, py) = camera.toPhoto(down.position.x, down.position.y, w, h)
                        onStrokeStart(px, py)
                        dibujando = true
                    }

                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val activos = ev.changes.filter { it.pressed }
                        if (activos.isEmpty()) break

                        if (activos.size >= 2) {
                            // Llegó el segundo dedo: se descarta el trazo a
                            // medias y pasamos a mover/ampliar.
                            if (dibujando) {
                                dibujando = false
                                onStrokeCancel()
                            }
                            multiTouch = true
                            val zoom = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val centro = ev.calculateCentroid(useCurrent = true)
                            if (centro != Offset.Unspecified) {
                                var c = camera
                                if (zoom != 1f) c = c.zoomBy(zoom, centro.x, centro.y, w, h)
                                if (pan != Offset.Zero) c = c.panBy(pan.x, pan.y, w, h)
                                if (c != camera) setCamera(c)
                            }
                            ev.changes.forEach { it.consume() }
                            continue
                        }

                        val ch: PointerInputChange = activos.first()
                        movido += (ch.position - ultimo).getDistance()
                        ultimo = ch.position

                        if (dibujando) {
                            val (px, py) = camera.toPhoto(ch.position.x, ch.position.y, w, h)
                            onStrokePoint(px, py)
                            if (ch.positionChanged()) ch.consume()
                        } else if (!multiTouch && !camera.isIdentity && ch.positionChanged()) {
                            // Visor AMPLIADO: un dedo mueve la foto. Sin ampliar
                            // no se toca nada, y así la ficha sigue haciendo
                            // scroll con normalidad: el gesto solo se "captura"
                            // cuando el usuario ya ha decidido mirar de cerca.
                            val d = ch.position - ch.previousPosition
                            val movida = camera.panBy(d.x, d.y, w, h)
                            if (movida != camera) {
                                setCamera(movida)
                                ch.consume()
                            }
                            // Si la foto YA no puede moverse más en esa dirección
                            // (tope del recorte), el gesto NO se consume y se lo
                            // queda la ficha, que sigue con su scroll. Sin esto,
                            // con la foto ampliada el dedo se quedaba atrapado en
                            // ella y la ficha parecía atascada — reportado por
                            // Rodrigo probando el Redmi Note 12.
                        }
                    }

                    if (dibujando) {
                        if (movido < toqueMaxPx) {
                            // Fue un toque, no un trazo: se descarta lo empezado
                            // y se trata como "añadir un punto".
                            onStrokeCancel()
                            val (px, py) = camera.toPhoto(down.position.x, down.position.y, w, h)
                            onTap(px, py)
                        } else {
                            onStrokeEnd()
                        }
                    }
                    // El toque del VISOR no se resuelve aquí sino en el detector
                    // de abajo, que ESPERA por si viene un segundo toque. Si no,
                    // al hacer doble toque para ampliar se enfocaba de paso una
                    // vía que ni querías tocar.
                }
            }
            // El doble toque va en su propio detector para no enredar el bucle
            // de arriba: aquí solo interesa la posición, no el arrastre.
            .pointerInput(viewSize) {
                if (viewSize.width == 0) return@pointerInput
                val w = viewSize.width.toFloat()
                val h = viewSize.height.toFloat()
                detectTapGestures(
                    onDoubleTap = { p -> setCamera(camera.toggleZoomAt(p.x, p.y, w, h)) },
                    // onTap solo se dispara cuando ha pasado el margen del doble
                    // toque: por eso el foco vive aquí y no en el bucle de arriba.
                    onTap = { p ->
                        if (!editable) {
                            val (px, py) = camera.toPhoto(p.x, p.y, w, h)
                            onTap(px, py)
                        }
                    }
                )
            }
    ) {
        // La foto y las líneas se amplían juntas: una sola transformación para
        // las dos, así no pueden desalinearse nunca.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = camera.scale, scaleY = camera.scale,
                    translationX = camera.offsetX, translationY = camera.offsetY,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                )
        ) {
            content(camera)
        }

    }
}
