package com.meteomontana.android.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.School
import java.io.File

/**
 * Genera una card de condiciones estilo Cumbre (papel/tinta/terracota) como
 * imagen PNG y abre el share sheet. Mucho más viral en WhatsApp que un texto:
 * nombre, score grande, etiqueta, datos clave y heatmap de las próximas 16h.
 */
fun shareSchoolAsImage(context: Context, school: School, forecast: Forecast?) {
    val bmp = renderConditionsCard(school, forecast)
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "condiciones.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "${school.name} — condiciones en Cumbre\n\nDescarga Cumbre:\nAndroid: https://play.google.com/store/apps/details?id=com.meteomontana.android\niOS: https://apps.apple.com/app/cumbre/id0000000000")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir condiciones"))
}

/* ── Paleta Cumbre (valores de Color.kt, en ARGB para Canvas) ──────────────── */
private const val PAPER = 0xFFFAF7F2.toInt()
private const val INK   = 0xFF1A1A1A.toInt()
private const val INK_SOFT = 0xFF6B6B6B.toInt()
private const val RULE  = 0xFFE2DCD2.toInt()
private const val TERRA = 0xFFC0532B.toInt()

private fun scoreColor(score: Int): Int = when {
    score >= 70 -> 0xFF4A7C59.toInt()
    score >= 50 -> 0xFFC8843A.toInt()
    else        -> 0xFFB94040.toInt()
}

private fun renderConditionsCard(school: School, forecast: Forecast?): Bitmap {
    val w = 1080
    val h = 720
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val pad = 64f

    c.drawColor(PAPER)
    // Borde regla fina, como las cards de la app
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = RULE
    }
    c.drawRect(12f, 12f, w - 12f, h - 12f, border)

    // Eyebrow "CONDICIONES DE ESCALADA"
    val eyebrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TERRA; textSize = 28f; typeface = Typeface.MONOSPACE
        letterSpacing = 0.18f; isFakeBoldText = true
    }
    c.drawText("CONDICIONES DE ESCALADA", pad, pad + 28f, eyebrow)

    // Nombre de la escuela (serif grande)
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; textSize = 76f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    c.drawText(school.name.take(22), pad, pad + 120f, title)

    // Subtítulo región · roca
    val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_SOFT; textSize = 32f }
    val subText = listOfNotNull(school.region, school.rockType).joinToString(" · ")
    if (subText.isNotEmpty()) c.drawText(subText, pad, pad + 170f, sub)

    val cur = forecast?.current
    if (cur != null) {
        // Score hero: badge cuadrado + etiqueta
        val badgeColor = scoreColor(cur.score)
        val badge = RectF(pad, 300f, pad + 180f, 480f)
        c.drawRoundRect(badge, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeColor })
        val scoreTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 96f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        c.drawText("${cur.score}", badge.centerX(), badge.centerY() + 12f, scoreTxt)
        val scoreSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
        c.drawText("/100", badge.centerX(), badge.bottom - 24f, scoreSub)

        // Datos a la derecha del badge
        val dataX = pad + 220f
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 44f; isFakeBoldText = true
        }
        c.drawText(cur.scoreLabel.uppercase(), dataX, 348f, label)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_SOFT; textSize = 34f }
        c.drawText("${cur.temperature.toInt()}°C · ${cur.humidity.toInt()}% hum · ${cur.windSpeed.toInt()} km/h", dataX, 404f, line)
        val rockLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (cur.dryRock) 0xFF4A7C59.toInt() else 0xFFB94040.toInt()
            textSize = 34f; isFakeBoldText = true
        }
        c.drawText(if (cur.dryRock) "● ROCA SECA" else "● ROCA MOJADA", dataX, 456f, rockLine)

        // Mejor ventana
        forecast.bestWindow?.let { wnd ->
            val wndPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 32f }
            c.drawText("Mejor ventana: ${wnd.start}–${wnd.end} (${wnd.avgScore}/100)", pad, 552f, wndPaint)
        }

        // Heatmap próximas 16h
        val hours = forecast.hours.take(16)
        if (hours.isNotEmpty()) {
            val cellW = (w - 2 * pad) / 16f
            val top = 590f
            hours.forEachIndexed { i, hr ->
                val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scoreColor(hr.score) }
                c.drawRect(pad + i * cellW, top, pad + (i + 1) * cellW - 4f, top + 36f, cell)
            }
            val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = INK_SOFT; textSize = 24f; typeface = Typeface.MONOSPACE; letterSpacing = 0.12f
            }
            c.drawText("PRÓXIMAS 16 HORAS", pad, top + 72f, hint)
        }
    }

    // Pie de marca
    val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TERRA; textSize = 28f; typeface = Typeface.MONOSPACE
        letterSpacing = 0.18f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
    }
    c.drawText("⛰ CUMBRE", w - pad, h - 48f, brand)

    return bmp
}
