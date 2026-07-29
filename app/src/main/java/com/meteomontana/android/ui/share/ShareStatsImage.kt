package com.meteomontana.android.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.meteomontana.android.domain.usecase.journal.JournalStatsCalculator
import java.io.File

/* Paleta Cumbre (= ShareProfileImage/ShareLineImage). */
private const val PAPER = 0xFFFAF7F2.toInt()
private const val INK = 0xFF1A1A1A.toInt()
private const val INK_SOFT = 0xFF6B6B6B.toInt()
private const val RULE = 0xFFE2DCD2.toInt()
private const val TERRA = 0xFFC0532B.toInt()

/**
 * Comparte MIS ESTADÍSTICAS como IMAGEN vertical 1080×1920 (formato historia,
 * estilo "Wrapped"): periodo elegido + métricas + pirámide de grados + racha.
 * Mismo patrón que el resto de shares: PNG a caché con nombre único →
 * FileProvider → share sheet (Instagram/WhatsApp).
 *
 * Los números vienen YA CALCULADOS del JournalStatsCalculator compartido —
 * esta capa solo pinta (la imagen enseña lo mismo que la pantalla).
 */
suspend fun shareStatsAsImage(
    context: Context,
    periodLabel: String,          // "MI 2026 EN ROCA" / "MI DIARIO EN ROCA"
    disciplineLabel: String,      // "BLOQUE" / "VÍA"
    summary: JournalStatsCalculator.Summary,
    maxGrade: String?
) {
    val bmp = renderStatsCard(periodLabel, disciplineLabel, summary, maxGrade)
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.filter { it.name.startsWith("stats") }?.forEach { it.delete() }
    val file = File(dir, "stats-${System.currentTimeMillis()}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(
            Intent.EXTRA_TEXT,
            "Mis estadísticas de escalada en Cumbre:\nhttps://api.climbingteams.com/app"
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir estadísticas"))
}

private fun renderStatsCard(
    periodLabel: String,
    disciplineLabel: String,
    s: JournalStatsCalculator.Summary,
    maxGrade: String?
): Bitmap {
    val w = 1080
    val h = 1920
    val cx = w / 2f
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(PAPER)
    c.drawRect(16f, 16f, w - 16f, h - 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = RULE
    })

    val mono = Typeface.MONOSPACE
    val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    c.drawText("⛰ CUMBRE · $disciplineLabel", cx, 150f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TERRA; textSize = 32f; typeface = mono
        letterSpacing = 0.18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
    })
    c.drawText(periodLabel, cx, 270f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; textSize = 96f; typeface = serifBold; textAlign = Paint.Align.CENTER
    })

    // ── 4 métricas en rejilla 2×2 ────────────────────────────────────────────
    val metrics = listOf(
        s.daysOut.toString() to "DÍAS DE ROCA",
        "${s.currentStreakWeeks} sem" to "RACHA",
        s.projectsFallen.toString() to "PROYECTOS CAÍDOS",
        (maxGrade ?: "—") to "GRADO MÁXIMO"
    )
    val boxW = (w - 200f) / 2f
    val boxH = 220f
    metrics.forEachIndexed { i, (value, label) ->
        val col = i % 2
        val row = i / 2
        val left = 80f + col * (boxW + 40f)
        val top = 360f + row * (boxH + 36f)
        val box = RectF(left, top, left + boxW, top + boxH)
        c.drawRect(box, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = RULE
        })
        c.drawText(value, box.centerX(), top + 118f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (label == "RACHA" || label == "GRADO MÁXIMO") TERRA else INK
            textSize = 86f; typeface = serifBold; textAlign = Paint.Align.CENTER
        })
        c.drawText(label, box.centerX(), top + 180f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_SOFT; textSize = 27f; typeface = mono
            letterSpacing = 0.14f; textAlign = Paint.Align.CENTER
        })
    }

    // ── Pirámide de grados ──────────────────────────────────────────────────
    var y = 950f
    c.drawText("PIRÁMIDE DE GRADOS", 80f, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK_SOFT; textSize = 30f; typeface = mono
        letterSpacing = 0.18f; isFakeBoldText = true
    })
    y += 50f
    val pyramid = s.pyramid.take(7)
    val maxCount = (pyramid.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val barMaxW = w - 420f
    pyramid.forEachIndexed { i, (grade, count) ->
        val gradePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (i == 0) TERRA else INK; textSize = 44f
            typeface = mono; isFakeBoldText = true
        }
        c.drawText(grade, 80f, y + 42f, gradePaint)
        val barW = barMaxW * count / maxCount.toFloat()
        val alpha = (255 * (1f - i * 0.09f)).toInt().coerceAtLeast(90)
        c.drawRoundRect(
            RectF(220f, y, 220f + barW.coerceAtLeast(24f), y + 52f), 12f, 12f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TERRA; this.alpha = alpha }
        )
        c.drawText(count.toString(), 240f + barW.coerceAtLeast(24f), y + 42f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_SOFT; textSize = 36f })
        y += 78f
    }

    // ── Mejor mes ───────────────────────────────────────────────────────────
    s.bestMonth?.let { bm ->
        y += 40f
        val months = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        val label = runCatching {
            "Mejor mes: ${months[bm.substringAfter('-').toInt() - 1]} (${s.bestMonthCount} ascensos)"
        }.getOrDefault("Mejor mes: $bm")
        c.drawText(label, cx, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 44f; textAlign = Paint.Align.CENTER
        })
    }

    // ── Pie ─────────────────────────────────────────────────────────────────
    c.drawText("Descarga Cumbre", cx, h - 200f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; textSize = 44f; textAlign = Paint.Align.CENTER
    })
    c.drawText("⛰ CUMBRE", cx, h - 110f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TERRA; textSize = 40f; typeface = mono
        letterSpacing = 0.18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
    })
    return bmp
}
