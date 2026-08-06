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
    loupeEnabled: Boolean = true,
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
    // Dónde está el dedo mientras dibuja: solo para la lupa (null = sin lupa).
    var fingerAt by remember { mutableStateOf<Offset?>(null) }

    fun setCamera(c: TopoCamera) {
        camera = c
        onCameraChange(c)
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewSize = it }
            .background(Color.Black)
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
                        fingerAt = down.position
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
                                fingerAt = null
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
                            fingerAt = ch.position
                            if (ch.positionChanged()) ch.consume()
                        } else if (!multiTouch && !editable) {
                            // Visor: un dedo mueve la foto (solo si está ampliada).
                            if (!camera.isIdentity && ch.positionChanged()) {
                                val d = ch.position - ch.previousPosition
                                setCamera(camera.panBy(d.x, d.y, w, h))
                                ch.consume()
                            }
                        }
                    }

                    fingerAt = null
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
                    } else if (!multiTouch && movido < toqueMaxPx) {
                        val (px, py) = camera.toPhoto(down.position.x, down.position.y, w, h)
                        onTap(px, py)
                    }
                }
            }
            // El doble toque va en su propio detector para no enredar el bucle
            // de arriba: aquí solo interesa la posición, no el arrastre.
            .pointerInput(viewSize) {
                if (viewSize.width == 0) return@pointerInput
                val w = viewSize.width.toFloat()
                val h = viewSize.height.toFloat()
                detectTapGestures(
                    onDoubleTap = { p -> setCamera(camera.toggleZoomAt(p.x, p.y, w, h)) }
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

        if (loupeEnabled && editable) {
            TopoLoupe(fingerAt, camera, viewSize) { content(camera) }
        }
    }
}

/**
 * Lupa: mientras el dedo está apoyado, enseña ampliado lo que hay debajo.
 *
 * Resuelve algo que el zoom por sí solo no arregla — **el dedo tapa justo el
 * punto que quieres marcar**. Es lo mismo que hace iOS al seleccionar texto.
 * Se coloca encima del dedo, y baja si no cabe arriba.
 */
@Composable
private fun BoxScope.TopoLoupe(
    fingerAt: Offset?,
    camera: TopoCamera,
    viewSize: IntSize,
    content: @Composable BoxScope.() -> Unit
) {
    val p = fingerAt ?: return
    if (viewSize.width == 0) return
    val density = LocalDensity.current
    val ladoPx = with(density) { LOUPE_SIZE.toPx() }
    val margen = with(density) { 12.dp.toPx() }

    // Encima del dedo; si se sale por arriba, debajo.
    val x = (p.x - ladoPx / 2f).coerceIn(margen, viewSize.width - ladoPx - margen)
    val yArriba = p.y - ladoPx - margen * 2
    val y = if (yArriba < margen) (p.y + margen * 2)
            .coerceAtMost(viewSize.height - ladoPx - margen)
    else yArriba

    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(LOUPE_SIZE)
            .clip(CircleShape)
            .background(Color.Black)
    ) {
        // El mismo contenido, ampliado y centrado en el dedo.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = camera.scale * LOUPE_ZOOM,
                    scaleY = camera.scale * LOUPE_ZOOM,
                    translationX = -(p.x - camera.offsetX) * LOUPE_ZOOM + ladoPx / 2f
                            + camera.offsetX * 0f,
                    translationY = -(p.y - camera.offsetY) * LOUPE_ZOOM + ladoPx / 2f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                )
        ) {
            content()
        }
        // Cruz en el punto exacto: sin ella la lupa enseña roca pero no dice
        // DÓNDE va a caer el punto.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = 7f * density.density
                    drawCircle(Color.White, radius = r, center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density.density))
                }
        )
    }
}

private val LOUPE_SIZE = 116.dp
private const val LOUPE_ZOOM = 2.4f
