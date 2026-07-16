package com.meteomontana.android.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File

/* Paleta Cumbre (= ShareLineImage) */
private const val PAPER = 0xFFFAF7F2.toInt()
private const val INK = 0xFF1A1A1A.toInt()
private const val INK_SOFT = 0xFF6B6B6B.toInt()
private const val RULE = 0xFFE2DCD2.toInt()
private const val TERRA = 0xFFC0532B.toInt()

/**
 * Comparte un perfil como IMAGEN vertical 1080×1920 (formato historia):
 * avatar + nombre + @username + grado máximo + bio, con marca Cumbre.
 * En el share sheet aparece Instagram (→ Historia), WhatsApp, etc.
 *
 * `suspend` porque descarga la foto de perfil (Coil) si la hay; sin foto
 * pinta un monograma con la inicial. El texto que acompaña lleva el enlace
 * /s/u/{handle} de siempre (Stories no admite enlaces, pero WhatsApp/DM sí).
 */
suspend fun shareProfileAsImage(
    context: Context,
    handle: String,
    displayLabel: String,
    username: String?,
    photoUrl: String?,
    topGrade: String?,
    bio: String?,
    boulders: Int? = null,
    routes: Int? = null,
    schools: Int? = null
) {
    // Avatar (opcional): descarga en software para poder dibujarlo.
    val avatar: Bitmap? = photoUrl?.takeIf { it.isNotBlank() }?.let { url ->
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(url).allowHardware(false).build()
        )
        (result as? SuccessResult)?.drawable?.toBitmap()
    }

    val bmp = renderProfileCard(displayLabel, username, avatar, topGrade, bio, boulders, routes, schools)
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    // Nombre ÚNICO (WhatsApp cachea por URI; con nombre fijo repetía la 1ª imagen).
    dir.listFiles()?.filter { it.name.startsWith("perfil") }?.forEach { it.delete() }
    val file = File(dir, "perfil-${System.currentTimeMillis()}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val text = "Perfil de $displayLabel en Cumbre:\n" +
        "https://api.climbingteams.com/s/u/$handle"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir perfil"))
}

private fun renderProfileCard(
    displayLabel: String,
    username: String?,
    avatar: Bitmap?,
    topGrade: String?,
    bio: String?,
    boulders: Int?,
    routes: Int?,
    schools: Int?
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

    // Eyebrow superior.
    c.drawText("ESCALA CONMIGO EN CUMBRE", cx, 180f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TERRA; textSize = 34f; typeface = Typeface.MONOSPACE
        letterSpacing = 0.18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
    })

    // Avatar circular (o monograma con inicial).
    val avatarR = 210f
    val avatarCy = 560f
    if (avatar != null) {
        val d = (avatarR * 2).toInt()
        val scale = maxOf(d / avatar.width.toFloat(), d / avatar.height.toFloat())
        val shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    cx - avatar.width * scale / 2f,
                    avatarCy - avatar.height * scale / 2f
                )
            })
        }
        c.drawCircle(cx, avatarCy, avatarR, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    } else {
        c.drawCircle(cx, avatarCy, avatarR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULE })
        c.drawText(
            displayLabel.trim().firstOrNull()?.uppercase() ?: "C", cx, avatarCy + 65f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TERRA; textSize = 190f; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
        )
    }
    // Anillo terra alrededor del avatar.
    c.drawCircle(cx, avatarCy, avatarR + 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = TERRA
    })

    // Nombre (serif, centrado).
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; textSize = 92f; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    var y = avatarCy + avatarR + 140f
    centeredWrap(displayLabel, namePaint, w - 160f, maxLines = 2).forEach { line ->
        c.drawText(line, cx, y, namePaint)
        y += 102f
    }

    // @username.
    if (!username.isNullOrBlank()) {
        c.drawText("@$username", cx, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_SOFT; textSize = 46f; textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        })
        y += 90f
    }

    // Grado máximo en caja regla (si lo hay).
    if (!topGrade.isNullOrBlank()) {
        y += 30f
        val label = "GRADO MÁXIMO"
        val gradePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 84f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
        val boxW = maxOf(gradePaint.measureText(topGrade) + 120f, 320f)
        val box = RectF(cx - boxW / 2f, y - 40f, cx + boxW / 2f, y + 160f)
        c.drawRect(box, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = RULE
        })
        c.drawText(label, cx, y + 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_SOFT; textSize = 28f; typeface = Typeface.MONOSPACE
            letterSpacing = 0.18f; textAlign = Paint.Align.CENTER
        })
        c.drawText(topGrade, cx, y + 118f, gradePaint)
        y = box.bottom + 110f
    } else {
        y += 40f
    }

    // Fila de stats: BLOQUES · VÍAS · ESCUELAS (solo las que tengan dato > 0).
    val statCols = listOfNotNull(
        boulders?.takeIf { it > 0 }?.let { "$it" to "BLOQUES" },
        routes?.takeIf { it > 0 }?.let { "$it" to "VÍAS" },
        schools?.takeIf { it > 0 }?.let { "$it" to "ESCUELAS" }
    )
    if (statCols.isNotEmpty()) {
        y += 20f
        val colW = (w - 240f) / statCols.size
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 76f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_SOFT; textSize = 28f; typeface = Typeface.MONOSPACE
            letterSpacing = 0.16f; textAlign = Paint.Align.CENTER
        }
        statCols.forEachIndexed { i, (num, label) ->
            val colCx = 120f + colW * i + colW / 2f
            c.drawText(num, colCx, y + 60f, numPaint)
            c.drawText(label, colCx, y + 110f, labelPaint)
        }
        y += 190f
    }

    // Bio (hasta 3 líneas, centrada).
    if (!bio.isNullOrBlank()) {
        val bioPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_SOFT; textSize = 42f; textAlign = Paint.Align.CENTER
        }
        centeredWrap(bio, bioPaint, w - 200f, maxLines = 3).forEach { line ->
            c.drawText(line, cx, y, bioPaint)
            y += 56f
        }
    }

    // Pie: CTA + marca.
    c.drawText("Descarga Cumbre", cx, h - 200f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; textSize = 44f; textAlign = Paint.Align.CENTER
    })
    c.drawText("⛰ CUMBRE", cx, h - 110f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TERRA; textSize = 40f; typeface = Typeface.MONOSPACE
        letterSpacing = 0.18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
    })

    return bmp
}

/** Parte `text` en hasta `maxLines` líneas que quepan en `maxWidth` (con "…"). */
private fun centeredWrap(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
    if (paint.measureText(text) <= maxWidth) return listOf(text)
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth) current = candidate
        else {
            if (current.isNotEmpty()) lines.add(current)
            current = word
            if (lines.size == maxLines - 1) break
        }
    }
    if (current.isNotEmpty() && lines.size < maxLines) lines.add(current)
    if (lines.size == maxLines) {
        var last = lines[maxLines - 1]
        while (paint.measureText("$last…") > maxWidth && last.isNotEmpty()) last = last.dropLast(1)
        lines[maxLines - 1] = "$last…"
    }
    return lines
}
