package com.meteomontana.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Efecto que un admin pone alrededor de la foto (Álvaro, 2026-10-19): HADAS doradas o DRAGONES rojos con las figuras
 * recortadas de su diseño (`effect_*` en drawable-nodpi). Animado en el perfil (dan vueltas mirando siempre hacia la
 * foto, con estela de brillos o brasas) y QUIETO en el feed, comentarios y chats. Sustituye a la aureola normal.
 * Mismos números que la maqueta aprobada; espejo de TitleEffectHalo.swift.
 */
enum class TitleEffectKind(
    val ring: Color, val highlight: Color, val particle: Color,
    val sprites: List<Int>, val facesRight: List<Boolean>,
    val count: Int, val orbit: Float, val size: Float, val speed: Float, val embers: Boolean,
    val staticAngles: List<Float>, val staticSize: Float
) {
    FAIRIES(
        Color(212, 162, 28), Color(255, 236, 170), Color(255, 236, 170),
        listOf(R.drawable.effect_fairy1, R.drawable.effect_fairy2, R.drawable.effect_fairy3, R.drawable.effect_fairy4),
        listOf(true, true, true, true),
        count = 4, orbit = 1.72f, size = 1.25f, speed = 0.42f, embers = false,
        staticAngles = listOf(-2.3f, 0.3f), staticSize = 0.62f
    ),
    DRAGONS(
        Color(196, 40, 28), Color(255, 170, 90), Color(255, 120, 40),
        listOf(R.drawable.effect_dragon1, R.drawable.effect_dragon2),
        listOf(true, false),
        count = 2, orbit = 1.62f, size = 1.85f, speed = 0.33f, embers = true,
        staticAngles = listOf(-2.2f), staticSize = 0.95f
    );

    companion object {
        fun from(raw: String?): TitleEffectKind? = when (raw?.uppercase()) {
            "FAIRIES" -> FAIRIES
            "DRAGONS" -> DRAGONS
            else -> null
        }
    }
}

/** Se pone ANTES del `.size()/.clip()` de la foto (como la aureola): dibuja por fuera sin mover nada. */
@Composable
fun Modifier.titleEffectHalo(kind: TitleEffectKind, size: Dp, animated: Boolean): Modifier {
    val images: List<ImageBitmap> = kind.sprites.map { ImageBitmap.imageResource(it) }
    val time by produceState(0f, animated) {
        if (!animated) return@produceState
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    val ringWidth = (size * (if (animated) 0.07f else 0.11f)).coerceAtLeast(2.dp)
    // Animado (perfil): hueco encima y debajo para las figuras. Sin él, la ficha las cortaba por arriba y el dragón
    // tapaba el nombre (captura de Álvaro, 2026-10-19). Va ANTES del dibujo: el centro sigue siendo el de la foto.
    val room = if (animated) this.padding(top = size * 0.66f, bottom = size * 0.45f) else this
    return room.drawWithContent {
        val t = if (animated) time else 0f
        val c = center
        val r = this.size.minDimension / 2f
        drawGlow(kind, c, r, animated)
        if (animated) drawRibbons(kind, c, r, t)
        drawContent()
        drawEffectRing(kind, c, r, t, ringWidth.toPx())
        drawTwinkles(kind, c, r, t, animated)
        drawFigures(kind, images, c, r, t, animated)
    }
}

private fun DrawScope.drawGlow(k: TitleEffectKind, c: Offset, r: Float, animated: Boolean) {
    val outer = r * (if (animated) 2.1f else 1.6f)
    drawCircle(
        Brush.radialGradient(0.85f * r / outer to k.ring.copy(alpha = 0.38f), 1f to k.ring.copy(alpha = 0f), center = c, radius = outer),
        radius = outer, center = c
    )
}

/** Tres «cintas» de luz que giran (solo animado). */
private fun DrawScope.drawRibbons(k: TitleEffectKind, c: Offset, r: Float, t: Float) {
    for (i in 0 until 3) {
        val rx = r * (1.55f + i * 0.12f)
        val ry = r * (0.55f + i * 0.1f)
        val deg = Math.toDegrees((t * 0.32f * (if (i % 2 == 0) 1 else -1) + i * 2.1f).toDouble()).toFloat()
        rotate(deg, c) {
            drawArc(
                brush = Brush.horizontalGradient(
                    listOf(k.ring.copy(alpha = 0f), k.highlight.copy(alpha = 0.9f), k.ring.copy(alpha = 0f)),
                    startX = c.x - rx, endX = c.x + rx
                ),
                startAngle = 18f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2, ry * 2),
                style = Stroke(width = if (i == 1) 2.5.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.drawEffectRing(k: TitleEffectKind, c: Offset, r: Float, t: Float, w: Float) {
    rotate(Math.toDegrees((t * 1.1f).toDouble()).toFloat(), c) {
        drawCircle(
            Brush.sweepGradient(listOf(k.ring, k.highlight, k.ring, k.highlight.copy(alpha = 0.85f), k.ring), center = c),
            radius = r + w / 2, center = c, style = Stroke(w)
        )
    }
}

/** Estrellitas (hadas) o brasas (dragones) fijas alrededor que parpadean. */
private fun DrawScope.drawTwinkles(k: TitleEffectKind, c: Offset, r: Float, t: Float, animated: Boolean) {
    val count = if (animated) 18 else 6
    for (i in 0 until count) {
        val angle = i * 2.399963f + (if (animated) t * 0.06f else 0f)          // ángulo áureo: repartidas
        val dist = if (animated) 1.25f + (i % 7) / 7f * 0.85f else 1.1f + (i % 3) * 0.07f
        val alpha = if (animated) 0.25f + 0.75f * abs(sin(t * 1.7f + i * 1.3f)) else 0.8f
        val p = Offset(c.x + cos(angle) * r * dist, c.y + sin(angle) * r * dist)
        val s = r * (if (animated) 0.05f + 0.04f * (i % 3) else 0.07f)
        sparkle(k, p, s, alpha)
    }
}

private fun figurePos(k: TitleEffectKind, c: Offset, r: Float, angle: Float, i: Int, t: Float, animated: Boolean): Offset {
    val orbit = if (animated) k.orbit else 1.18f
    val bob = if (animated) sin(t * 2f + i) * r * 0.08f else 0f
    return Offset(c.x + cos(angle) * r * orbit, c.y + sin(angle) * r * orbit * (if (animated) 0.9f else 1f) + bob)
}

private fun DrawScope.drawFigures(k: TitleEffectKind, images: List<ImageBitmap>, c: Offset, r: Float, t: Float, animated: Boolean) {
    val n = if (animated) k.count else k.staticAngles.size
    for (i in 0 until n) {
        val angle = if (animated) t * k.speed + i * 2f * PI.toFloat() / k.count else k.staticAngles[i]
        if (animated) drawTrail(k, c, r, i, t)
        val pos = figurePos(k, c, r, angle, i, t, animated)
        val img = images[i % images.size]
        val h = r * (if (animated) k.size else k.staticSize)
        val w = h * img.width / img.height
        // Siempre mirando hacia la foto: a la izquierda del centro miran a la derecha y al revés.
        val flip = (pos.x < c.x) != k.facesRight[i % images.size]
        withTransform({
            translate(pos.x, pos.y)
            if (flip) scale(-1f, 1f, Offset.Zero)
        }) {
            drawImage(img, dstOffset = IntOffset((-w / 2).roundToInt(), (-h / 2).roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()))
        }
    }
}

/** Estela: brillos (o brasas) en las posiciones por las que acaba de pasar, cada vez más tenues. Sin estado. */
private fun DrawScope.drawTrail(k: TitleEffectKind, c: Offset, r: Float, i: Int, t: Float) {
    for (step in 1..7) {
        val past = t - step * 0.11f
        val angle = past * k.speed + i * 2f * PI.toFloat() / k.count
        val p = figurePos(k, c, r, angle, i, past, true)
        val shifted = Offset(p.x, p.y + r * (if (k.embers) -0.1f * step else 0.35f))
        sparkle(k, shifted, r * (0.09f - 0.008f * step), (1f - step / 8f) * 0.85f)
    }
}

/** Estrella de cuatro puntas (hadas) o brasa en rombo (dragones). */
private fun DrawScope.sparkle(k: TitleEffectKind, p: Offset, s: Float, alpha: Float) {
    val path = Path().apply {
        if (k.embers) {
            moveTo(p.x, p.y - s * 1.6f); lineTo(p.x + s * 0.7f, p.y); lineTo(p.x, p.y + s * 0.9f); lineTo(p.x - s * 0.7f, p.y); close()
        } else {
            moveTo(p.x, p.y - s)
            quadraticTo(p.x, p.y, p.x + s, p.y)
            quadraticTo(p.x, p.y, p.x, p.y + s)
            quadraticTo(p.x, p.y, p.x - s, p.y)
            quadraticTo(p.x, p.y, p.x, p.y - s)
        }
    }
    drawPath(path, k.particle.copy(alpha = alpha.coerceIn(0f, 1f)))
}
