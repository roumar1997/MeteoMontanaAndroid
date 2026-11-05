package com.meteomontana.android.ui.screens.detail.guide

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.Typeface
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.schools.GuideSector
import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.components.startTypeLabelRes
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Medidas, colores y piezas de dibujo del PDF de la guía (sin estado: el maquetador, `Writer` en GuidePdf.kt,
 * decide QUÉ va en cada página; aquí solo se dibuja). Separado para que cada fichero tenga una sola razón de cambio.
 */
internal object GuidePdfKit {
    const val W = GuidePdf.PAGE_W
    const val H = GuidePdf.PAGE_H
    const val MARGIN = 32f
    const val CONTENT_W = W - MARGIN * 2
    const val BOTTOM = H - 46

    const val DARK = 0xFF14120F.toInt()
    const val DARK_CARD = 0xFF24201B.toInt()
    const val PAPER = 0xFFF7F4EE.toInt()
    const val CHIP = 0xFFECE5D9.toInt()
    const val TERRA = 0xFFB4441C.toInt()
    const val ACCENT = 0xFFE0743F.toInt()
    const val MUTED = 0xFF8A8076.toInt()
    const val MUTED_DARK = 0xFFA89E92.toInt()
    const val LINE = 0xFFEFEAE2.toInt()
    const val BOX = 0xFFCFC6B8.toInt()
    const val DESC = 0xFF4A423B.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Nombre de destino del PDF para la tarjeta de una piedra. */
    fun blockDest(blockId: String) = "b" + blockId.filter { it.isLetterOrDigit() }

    fun tp(size: Float, tf: Typeface, color: Int, kern: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; typeface = tf; this.color = color
        if (kern != 0f) letterSpacing = kern / size
    }

    fun lineH(p: Paint) = p.descent() - p.ascent()

    private fun layout(s: String, p: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, p, max(1, width.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()

    fun measure(s: String, p: TextPaint, width: Float): Float = ceil(layout(s, p, width).height.toFloat())

    fun pillSize(s: String, p: Paint, padH: Float = 10f, padV: Float = 5f) =
        (ceil(p.measureText(s)) + padH * 2) to (ceil(lineH(p)) + padV * 2)

    fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    fun stroke(color: Int, w: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w }
    fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255).toInt() shl 24)

    fun pdfSectorTitle(s: GuideSector): String =
        if (s.unknownFace) AppText.get(R.string.sguide_unknown_face) else s.sector ?: AppText.get(R.string.blocks_no_sector)

    /** Salida y estrellas de una vía, en una línea («Sentado · ★ 4.0»). */
    fun pdfMeta(h: LineSearchHit): String? {
        val parts = mutableListOf<String>()
        startTypeLabelRes(h.startType)?.let { parts.add(AppText.get(it)) }
        val avg = h.rating
        if (avg != null && (h.ratingCount ?: 0) > 0) parts.add("★ " + String.format(Locale.ROOT, "%.1f", avg))
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** Texto que puede ocupar varias líneas; devuelve su alto. */
    fun Canvas.textBlock(s: String, p: TextPaint, x: Float, y: Float, width: Float): Float {
        val l = layout(s, p, width)
        save(); translate(x, y); l.draw(this); restore()
        return ceil(l.height.toFloat())
    }

    /** Píldora con texto; (x, y) es su esquina superior izquierda. Devuelve su rectángulo. */
    fun Canvas.pill(
        s: String, p: Paint, bg: Int, x: Float, y: Float, padH: Float = 10f, padV: Float = 5f,
        radius: Float? = null, border: Int? = null
    ): RectF {
        val (w, h) = pillSize(s, p, padH, padV)
        val r = RectF(x, y, x + w, y + h)
        val rad = radius ?: (h / 2)
        drawRoundRect(r, rad, rad, fill(bg))
        if (border != null) drawRoundRect(r, rad, rad, stroke(border, 1.2f))
        drawText(s, x + padH, y + padV - p.ascent(), p)
        return r
    }

    fun Canvas.centered(s: String, p: Paint, r: RectF) {
        drawText(s, r.centerX() - p.measureText(s) / 2, r.centerY() - (p.ascent() + p.descent()) / 2, p)
    }

    inline fun Canvas.clipRound(r: RectF, radius: Float, draw: () -> Unit) {
        save()
        clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
        draw()
        restore()
    }

    /** Píldora de la marca: montaña + CUMBRE (+ escuela en gris). Devuelve su rectángulo. */
    fun Canvas.brandPill(f: Fonts, x: Float, y: Float, school: String?, bg: Int): RectF {
        val brandP = tp(8.5f, f.monoBold, WHITE, kern = 2.2f)
        val schoolP = tp(8f, f.mono, MUTED_DARK, kern = 1.2f)
        val schoolT = school?.uppercase()
        val w = 11 + 15 + 6 + brandP.measureText("CUMBRE") + (schoolT?.let { 7 + schoolP.measureText(it) } ?: 0f) + 11
        val r = RectF(x, y, x + ceil(w), y + 22)
        drawRoundRect(r, 11f, 11f, fill(bg))
        mountain(r.left + 11, r.top + 6, 0.75f)
        var tx = r.left + 11 + 15 + 6
        drawText("CUMBRE", tx, r.centerY() - (brandP.ascent() + brandP.descent()) / 2, brandP)
        tx += brandP.measureText("CUMBRE") + 7
        if (schoolT != null) drawText(schoolT, tx, r.centerY() - (schoolP.ascent() + schoolP.descent()) / 2, schoolP)
        return r
    }

    /** La montaña de la marca (un trazo), en un rectángulo de 20×14 escalado. */
    private fun Canvas.mountain(ox: Float, oy: Float, scale: Float) {
        val pts = listOf(1f to 13f, 7f to 3f, 10f to 8f, 13f to 4f, 19f to 13f)
        val path = Path()
        pts.forEachIndexed { i, (px, py) ->
            if (i == 0) path.moveTo(ox + px * scale, oy + py * scale) else path.lineTo(ox + px * scale, oy + py * scale)
        }
        path.close()
        drawPath(path, stroke(ACCENT, 1.8f).apply { strokeJoin = Paint.Join.ROUND })
    }

}
