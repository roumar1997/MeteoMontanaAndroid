package com.meteomontana.android.ui.screens.gyms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.appcompat.content.res.AppCompatResources
import androidx.annotation.DrawableRes
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.ui.theme.gymFillArgb
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory

/**
 * Marcadores de rocódromos para MapLibre (solo acepta Bitmaps). Maqueta aprobada: círculo del color del tipo con
 * borde blanco y el icono dentro; el muro gratis lleva debajo la etiqueta «GRATIS». El punto del mapa es la base
 * del bitmap. Los iconos se CACHEAN por clave: registrar sprites nuevos en cada sincronización corrompe el
 * atlas de MapLibre (ver `cachedPanelIcon` de Escuelas).
 */
internal object GymMarkerIcons {
    private val cache = HashMap<String, Icon>()

    fun pin(ctx: Context, kind: GymKind, dark: Boolean, selected: Boolean, freeLabel: String): Icon =
        cache.getOrPut("pin:$kind:$dark:$selected:$freeLabel") {
            IconFactory.getInstance(ctx).fromBitmap(pinBitmap(ctx, kind, dark, selected, freeLabel))
        }

    fun cluster(ctx: Context, count: Int, dark: Boolean): Icon =
        cache.getOrPut("cluster:$count:$dark") {
            IconFactory.getInstance(ctx).fromBitmap(clusterBitmap(ctx, count, dark))
        }

    @DrawableRes
    fun glyph(kind: GymKind): Int = when (kind) {
        GymKind.COMMERCIAL -> R.drawable.ic_gym
        GymKind.MUNICIPAL -> R.drawable.ic_municipal
        GymKind.FREE -> R.drawable.ic_free_wall
    }

    internal fun pinBitmap(ctx: Context, kind: GymKind, dark: Boolean, selected: Boolean, freeLabel: String): Bitmap {
        val k = ctx.resources.displayMetrics.density
        val d = (if (selected) 46f else 38f) * k
        val ring = 3f * k
        val pad = ring + 2f * k                      // sitio para el aro de selección y la sombra
        val pill = if (kind == GymKind.FREE) pillHeight(k) else 0f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 9f * k; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f
        }
        val pillW = if (kind == GymKind.FREE) labelPaint.measureText(freeLabel) + 14f * k else 0f
        val w = maxOf(d, pillW) + pad * 2
        val h = d + pad * 2 + pill - (if (kind == GymKind.FREE) 4f * k else 0f)
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        val cy = pad + d / 2f
        val fill = gymFillArgb(kind, dark)

        c.drawCircle(cx, cy + 2f * k, d / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) })
        if (selected) {
            c.drawCircle(cx, cy, d / 2f + ring, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (fill and 0x00FFFFFF) or 0x55000000 })
        }
        c.drawCircle(cx, cy, d / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        c.drawCircle(cx, cy, d / 2f - ring / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = ring; color = Color.WHITE
        })
        drawGlyph(ctx, c, glyph(kind), cx, cy, d * 0.52f)

        if (kind == GymKind.FREE) {
            val top = cy + d / 2f - 4f * k + 2f * k
            val r = RectF(cx - pillW / 2f, top, cx + pillW / 2f, top + pill - 2f * k)
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gymFillArgb(kind, dark) })
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.5f * k; color = Color.WHITE
            })
            val baseline = r.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
            c.drawText(freeLabel, cx - labelPaint.measureText(freeLabel) / 2f, baseline, labelPaint)
        }
        return bmp
    }

    private fun pillHeight(k: Float) = 16f * k

    internal fun clusterBitmap(ctx: Context, count: Int, dark: Boolean): Bitmap {
        val k = ctx.resources.displayMetrics.density
        val d = (if (count >= 100) 48f else 40f) * k
        val pad = 4f * k
        val size = (d + pad * 2).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        c.drawCircle(cx, cx + 2f * k, d / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) })
        // Tinta de marca (los botones "negros"): en oscuro un poco más clara para que no se funda con el fondo.
        c.drawCircle(cx, cx, d / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0xFF2A281F.toInt() else 0xFF1C1C1A.toInt() })
        c.drawCircle(cx, cx, d / 2f - 1.5f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f * k; color = Color.WHITE
        })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 15f * k; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        c.drawText(count.toString(), cx, cx - (text.descent() + text.ascent()) / 2f, text)
        return bmp
    }

    private fun drawGlyph(ctx: Context, canvas: Canvas, @DrawableRes res: Int, cx: Float, cy: Float, size: Float) {
        val drawable = AppCompatResources.getDrawable(ctx, res)?.mutate() ?: return
        drawable.setTint(Color.WHITE)
        val half = (size / 2f).toInt()
        drawable.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        drawable.draw(canvas)
    }
}
