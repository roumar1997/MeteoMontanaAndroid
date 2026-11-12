package com.meteomontana.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.meteomontana.android.domain.model.TitleEffectSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Efecto que un admin pone alrededor de la foto, DIRIGIDO POR EL SERVIDOR (GET /api/title-effects): sprites, colores
 * y movimiento vienen del catálogo, así que un efecto nuevo no necesita versión. Animado en el perfil y QUIETO en el
 * feed, comentarios y chats. Sustituye a la aureola normal. Espejo de TitleEffectHalo.swift.
 * - FACE_PHOTO (hadas, dragones): las figuras dan vueltas mirando siempre hacia la foto.
 * - ORBIT (Furgo Lola): las figuras «conducen» alrededor, tangentes al círculo, ruedas hacia la foto, sentido horario.
 */
private class EffectPalette(spec: TitleEffectSpec) {
    val ring: List<Color> = spec.ringColors.mapNotNull { colorFromHexOrNull(it) }.ifEmpty { listOf(Color.White) }
    val glow: Color = colorFromHexOrNull(spec.glowColor) ?: ring.first()
    val highlight: Color = colorFromHexOrNull(spec.highlightColor) ?: Color.White
    val particle: Color = colorFromHexOrNull(spec.particleColor) ?: Color.White
}

/** Se pone ANTES del `.size()/.clip()` de la foto (como la aureola): dibuja por fuera sin mover nada. */
@Composable
fun Modifier.titleEffectHalo(spec: TitleEffectSpec, size: Dp, animated: Boolean): Modifier {
    val images = rememberEffectSprites(spec)
    val time = rememberEffectClock(animated)
    val palette = remember(spec) { EffectPalette(spec) }
    val ringWidth = (size * (if (animated) 0.07f else 0.11f)).coerceAtLeast(2.dp)
    // Animado (perfil): hueco encima y debajo para las figuras. Sin él, la ficha las cortaba por arriba y el dragón
    // tapaba el nombre (captura de Álvaro, 2026-10-19). Va ANTES del dibujo: el centro sigue siendo el de la foto.
    val room = if (animated) this.padding(top = size * 0.5f, bottom = size * 0.42f) else this
    return room.drawWithContent {
        val t = if (animated) time.value else 0f          // leído aquí: solo redibuja, no recompone
        val c = center
        val r = this.size.minDimension / 2f
        drawGlow(palette, c, r, animated)
        if (animated) drawRibbons(palette, c, r, t)
        drawContent()
        drawEffectRing(palette, c, r, t, ringWidth.toPx())
        drawTwinkles(spec, palette, c, r, t, animated)
        drawFigures(spec, palette, images.value, c, r, t, animated)
    }
}

private fun DrawScope.drawGlow(p: EffectPalette, c: Offset, r: Float, animated: Boolean) {
    val outer = r * (if (animated) 2.1f else 1.6f)
    drawCircle(
        Brush.radialGradient(0.85f * r / outer to p.glow.copy(alpha = 0.38f), 1f to p.glow.copy(alpha = 0f), center = c, radius = outer),
        radius = outer, center = c
    )
}

/** Tres «cintas» de luz que giran (solo animado). */
private fun DrawScope.drawRibbons(p: EffectPalette, c: Offset, r: Float, t: Float) {
    for (i in 0 until 3) {
        val rx = r * (1.55f + i * 0.12f)
        val ry = r * (0.55f + i * 0.1f)
        val deg = Math.toDegrees((t * 0.32f * (if (i % 2 == 0) 1 else -1) + i * 2.1f).toDouble()).toFloat()
        rotate(deg, c) {
            drawArc(
                brush = Brush.horizontalGradient(
                    listOf(p.glow.copy(alpha = 0f), p.highlight.copy(alpha = 0.9f), p.glow.copy(alpha = 0f)),
                    startX = c.x - rx, endX = c.x + rx
                ),
                startAngle = 18f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2, ry * 2),
                style = Stroke(width = if (i == 1) 2.5.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/** Aro con el degradado del catálogo, cerrado (vuelve al primer color) y girando. */
private fun DrawScope.drawEffectRing(p: EffectPalette, c: Offset, r: Float, t: Float, w: Float) {
    rotate(Math.toDegrees((t * 1.1f).toDouble()).toFloat(), c) {
        drawCircle(Brush.sweepGradient(p.ring + p.ring.first(), center = c), radius = r + w / 2, center = c, style = Stroke(w))
    }
}

/** Estrellitas o brasas fijas alrededor que parpadean. */
private fun DrawScope.drawTwinkles(spec: TitleEffectSpec, p: EffectPalette, c: Offset, r: Float, t: Float, animated: Boolean) {
    val count = if (animated) 18 else 6
    for (i in 0 until count) {
        val angle = i * 2.399963f + (if (animated) t * 0.06f else 0f)          // ángulo áureo: repartidas
        val dist = if (animated) 1.25f + (i % 7) / 7f * 0.85f else 1.1f + (i % 3) * 0.07f
        val alpha = if (animated) 0.25f + 0.75f * abs(sin(t * 1.7f + i * 1.3f)) else 0.8f
        val pos = Offset(c.x + cos(angle) * r * dist, c.y + sin(angle) * r * dist)
        val s = r * (if (animated) 0.05f + 0.04f * (i % 3) else 0.07f)
        sparkle(spec.embers, p.particle, pos, s, alpha)
    }
}

private fun figureAngle(spec: TitleEffectSpec, i: Int, t: Float): Float =
    t * spec.motion.speed + i * 2f * PI.toFloat() / spec.figureCount

/** ORBIT: círculo exacto (para que la furgo vaya tangente). FACE_PHOTO: algo achatado y flotando. */
private fun figurePos(spec: TitleEffectSpec, c: Offset, r: Float, angle: Float, i: Int, t: Float, animated: Boolean): Offset {
    val orbit = if (animated) spec.motion.orbit else 1.18f
    if (spec.orbits) return Offset(c.x + cos(angle) * r * orbit, c.y + sin(angle) * r * orbit)
    val bob = if (animated) sin(t * 2f + i) * r * 0.08f else 0f
    return Offset(c.x + cos(angle) * r * orbit, c.y + sin(angle) * r * orbit * (if (animated) 0.9f else 1f) + bob)
}

private fun DrawScope.drawFigures(
    spec: TitleEffectSpec, p: EffectPalette, images: List<ImageBitmap?>, c: Offset, r: Float, t: Float, animated: Boolean
) {
    if (spec.sprites.isEmpty()) return
    val n = if (animated) spec.figureCount else spec.motion.staticAngles.size
    for (i in 0 until n) {
        val angle = if (animated) figureAngle(spec, i, t) else spec.motion.staticAngles[i]
        if (animated) drawTrail(spec, p, c, r, i, t)
        // ORBIT: siempre una figura que mire a la derecha (sentido horario); voltearla pondría el rótulo en espejo.
        val si = (if (spec.orbits) spec.sprites.indexOfFirst { it.facesRight }.takeIf { it >= 0 } else null)
            ?: (i % spec.sprites.size)
        val img = images.getOrNull(si % images.size) ?: continue
        val facesRight = spec.sprites[si].facesRight
        val pos = figurePos(spec, c, r, angle, i, t, animated)
        val h = r * (if (animated) spec.motion.size else spec.motion.staticSize)
        val w = h * img.width / img.height
        withTransform({
            translate(pos.x, pos.y)
            if (spec.orbits) {
                // Tangente al círculo: con +90° la parte de abajo apunta al centro y el morro avanza en sentido horario.
                rotate(Math.toDegrees(angle.toDouble()).toFloat() + 90f, Offset.Zero)
                if (!facesRight) scale(-1f, 1f, Offset.Zero)
            } else if ((pos.x < c.x) != facesRight) {
                // Mirando a la foto: a la izquierda del centro miran a la derecha y al revés.
                scale(-1f, 1f, Offset.Zero)
            }
        }) {
            drawImage(img, dstOffset = IntOffset((-w / 2).roundToInt(), (-h / 2).roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()))
        }
    }
}

/** Estela: brillos (o brasas) en las posiciones por las que acaba de pasar, cada vez más tenues. Sin estado. */
private fun DrawScope.drawTrail(spec: TitleEffectSpec, p: EffectPalette, c: Offset, r: Float, i: Int, t: Float) {
    for (step in 1..7) {
        val past = t - step * 0.11f
        val pos = figurePos(spec, c, r, figureAngle(spec, i, past), i, past, true)
        val dy = when {
            spec.orbits -> 0f
            spec.embers -> r * -0.1f * step
            else -> r * 0.35f
        }
        sparkle(spec.embers, p.particle, Offset(pos.x, pos.y + dy), r * (0.09f - 0.008f * step), (1f - step / 8f) * 0.85f)
    }
}

/** Estrella de cuatro puntas o brasa en rombo. */
private fun DrawScope.sparkle(embers: Boolean, color: Color, p: Offset, s: Float, alpha: Float) {
    val path = Path().apply {
        if (embers) {
            moveTo(p.x, p.y - s * 1.6f); lineTo(p.x + s * 0.7f, p.y); lineTo(p.x, p.y + s * 0.9f); lineTo(p.x - s * 0.7f, p.y); close()
        } else {
            moveTo(p.x, p.y - s)
            quadraticTo(p.x, p.y, p.x + s, p.y)
            quadraticTo(p.x, p.y, p.x, p.y + s)
            quadraticTo(p.x, p.y, p.x - s, p.y)
            quadraticTo(p.x, p.y, p.x, p.y - s)
        }
    }
    drawPath(path, color.copy(alpha = alpha.coerceIn(0f, 1f)))
}
