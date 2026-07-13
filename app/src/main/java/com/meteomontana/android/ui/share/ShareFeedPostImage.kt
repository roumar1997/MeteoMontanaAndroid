package com.meteomontana.android.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.meteomontana.android.domain.model.FeedPost
import com.meteomontana.android.domain.usecase.feed.FeedKind
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.renderTopo
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import java.io.File

/* ── Paleta Cumbre (ARGB para Canvas, = ShareLineImage) ────────────────────── */
private const val PAPER = 0xFFFAF7F2.toInt()
private const val INK = 0xFF1A1A1A.toInt()
private const val INK_SOFT = 0xFF6B6B6B.toInt()
private const val RULE = 0xFFE2DCD2.toInt()
private const val TERRA = 0xFFC0532B.toInt()

/**
 * Facebook App ID para el intent de Instagram Stories
 * (com.instagram.share.ADD_TO_STORY exige "source_application").
 *
 * ⚠️ PENDIENTE: cuando Rodrigo registre la app en developers.facebook.com,
 * pegar aquí el App ID (solo dígitos). Mientras esté VACÍO, el botón
 * "COMPARTIR EN HISTORIAS" no se muestra en la UI (ver canShareToStories()).
 */
const val FACEBOOK_APP_ID = ""

/** true si podemos ofrecer "COMPARTIR EN HISTORIAS" (hay App ID configurado). */
fun canShareToStories(): Boolean = FACEBOOK_APP_ID.isNotBlank()

/**
 * Comparte un post del feed como IMAGEN 1080×1920 (formato historia): foto de
 * la cara con SOLO la línea del post dibujada + cabecera Cumbre (tipo de
 * logro, vía/grado, piedra · escuela y autor @username).
 *
 * Si el post no tiene foto o trazo, se comparte igualmente una imagen tipo
 * tarjeta con los datos (misma cabecera, sin foto) — decisión: compartir
 * SIEMPRE imagen para un comportamiento uniforme; el texto que acompaña es
 * mínimo y SIN deep link (decisión del usuario).
 */
suspend fun shareFeedPostAsImage(context: Context, post: FeedPost) {
    val uri = renderPostToUri(context, post) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, plainText(post))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir"))
}

/**
 * Comparte el post directamente como historia de Instagram
 * (com.instagram.share.ADD_TO_STORY). Solo llamar si canShareToStories().
 */
suspend fun shareFeedPostToStories(context: Context, post: FeedPost) {
    if (!canShareToStories()) return
    val uri = renderPostToUri(context, post) ?: return
    val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
        setDataAndType(uri, "image/png")
        putExtra("source_application", FACEBOOK_APP_ID)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Concede el permiso de lectura explícitamente a Instagram.
    context.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
        .onFailure {
            // Instagram no instalado → cae al share sheet normal.
            shareFeedPostAsImage(context, post)
        }
}

/** Texto plano de acompañamiento — SIN enlace (decisión del usuario). */
private fun plainText(post: FeedPost): String {
    val title = buildString {
        append(post.lineName ?: post.blockName ?: "")
        post.grade?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }
    val place = listOfNotNull(
        post.blockName?.takeIf { it.isNotBlank() && post.lineName != null },
        post.schoolName?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    return listOf("🧗 $title", place).filter { it.isNotBlank() }.joinToString("\n")
}

/** Pinta la imagen del post y la deja en cache/share; null solo si falla el PNG. */
private suspend fun renderPostToUri(context: Context, post: FeedPost): Uri? {
    // Foto (si la hay) descargada en software para poder dibujarla.
    val photo: Bitmap? = post.photoPath?.takeIf { it.isNotBlank() }?.let { url ->
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }
    val bmp = renderPostCard(post, photo)
    return runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "feed-post.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

private fun kindEyebrow(post: FeedPost): String = when (post.kind) {
    FeedKind.PROJECT_DONE -> "PROYECTO CONSEGUIDO"
    FeedKind.NEW_BLOCK -> "PIEDRA NUEVA"
    FeedKind.NEW_LINE -> "VÍA NUEVA"
    else -> when {
        post.discipline.equals("ROUTE", ignoreCase = true) -> "VÍA HECHA"
        post.discipline.equals("BOULDER", ignoreCase = true) -> "BLOQUE HECHO"
        else -> "HECHO"
    }
}

private fun renderPostCard(post: FeedPost, photo: Bitmap?): Bitmap {
    val w = 1080
    val h = 1920
    val pad = 72f
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(PAPER)
    c.drawRect(16f, 16f, w - 16f, h - 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = RULE
    })

    // ── Cabecera: eyebrow del tipo de logro ──
    c.drawText(
        kindEyebrow(post), pad, pad + 40f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TERRA; textSize = 34f; typeface = Typeface.MONOSPACE
            letterSpacing = 0.18f; isFakeBoldText = true
        }
    )

    // Vía + grado (serif grande).
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; textSize = 78f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val title = buildString {
        append(post.lineName ?: post.blockName ?: "")
        post.grade?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }.ifBlank { "Ascenso" }
    val titleLines = wrapTextShare(title, titlePaint, w - 2 * pad, maxLines = 2)
    var y = pad + 140f
    titleLines.forEach { l -> c.drawText(l, pad, y, titlePaint); y += 86f }

    // Piedra · escuela (+ roca si viene).
    val place = listOfNotNull(
        post.blockName?.takeIf { it.isNotBlank() && post.lineName != null },
        post.schoolName?.takeIf { it.isNotBlank() },
        post.rockType?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    if (place.isNotBlank()) {
        c.drawText(place, pad, y - 4f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_SOFT; textSize = 38f })
        y += 48f
    }

    // Autor.
    val authorLabel = post.author.username?.let { "@$it" }
        ?: post.author.displayName ?: ""
    if (authorLabel.isNotBlank()) {
        c.drawText("por $authorLabel", pad, y,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TERRA; textSize = 36f; isFakeBoldText = true
            })
        y += 40f
    }
    y += 16f

    // ── Foto con SOLO la línea del post ──
    val footerH = 130f
    if (photo != null) {
        val avail = h - footerH - y - pad
        val availW = w - 2 * pad
        val ratio = if (photo.width <= 0 || photo.height <= 0) 4f / 3f
        else (photo.width.toFloat() / photo.height).coerceIn(0.55f, 2.2f)
        var rectW = availW
        var rectH = rectW / ratio
        if (rectH > avail) { rectH = avail; rectW = rectH * ratio }
        val left = (w - rectW) / 2f
        val top = y + (avail - rectH) / 2f
        val dst = RectF(left, top, left + rectW, top + rectH)

        val src = centerCropSrcShare(photo.width, photo.height, rectW / rectH)
        c.save()
        c.clipRect(dst)
        c.drawBitmap(photo, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))

        val points = parseLineStroke(post.linePath).points
        if (points.isNotEmpty()) {
            val s = rectW / 380f
            val ops = renderTopo(
                listOf(
                    TopoLineData(
                        name = post.lineName, grade = post.grade, startType = null,
                        points = points.map { it.x to it.y },
                        strokeWidthPx = 5f * s
                    )
                ),
                rectW, rectH,
                badgeR = (14f * s) to (11f * s),
                badgeTextPx = (22f * s) to (7f * s),
                startR = (22f * s) to (18f * s),
                startTextPx = (18f * s) to (6f * s)
            )
            ops.forEach { drawFeedOp(it, c, dst.left, dst.top) }
        }
        c.restore()
        c.drawRect(dst, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = RULE
        })
    } else {
        // Sin foto: tarjeta de datos centrada (grado enorme en terra).
        post.grade?.takeIf { it.isNotBlank() }?.let { g ->
            c.drawText(
                g, w / 2f, h / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = TERRA; textSize = 260f; textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }
            )
        }
    }

    // ── Pie de marca ──
    c.drawText(
        "⛰ CUMBRE", w - pad, h - 56f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TERRA; textSize = 34f; typeface = Typeface.MONOSPACE
            letterSpacing = 0.18f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
    )
    return bmp
}

/* Copias locales de los helpers de ShareLineImage (son private allí). */

private fun centerCropSrcShare(pw: Int, ph: Int, dstRatio: Float): Rect {
    val photoRatio = pw.toFloat() / ph
    return if (photoRatio > dstRatio) {
        val cropW = (ph * dstRatio).toInt()
        val x = (pw - cropW) / 2
        Rect(x, 0, x + cropW, ph)
    } else {
        val cropH = (pw / dstRatio).toInt()
        val yOff = (ph - cropH) / 2
        Rect(0, yOff, pw, yOff + cropH)
    }
}

private fun drawFeedOp(
    op: com.meteomontana.android.domain.model.DrawOp,
    c: Canvas, dx: Float, dy: Float
) {
    when (op) {
        is com.meteomontana.android.domain.model.DrawOp.LinePath -> {
            val path = android.graphics.Path()
            op.pts.forEachIndexed { i, (x, y) ->
                if (i == 0) path.moveTo(x + dx, y + dy) else path.lineTo(x + dx, y + dy)
            }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = op.widthPx; color = op.argb.toInt()
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                if (op.dashed) pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 20f), 0f)
            })
        }
        is com.meteomontana.android.domain.model.DrawOp.FilledCircle -> c.drawCircle(
            op.cx + dx, op.cy + dy, op.radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = op.argb.toInt() }
        )
        is com.meteomontana.android.domain.model.DrawOp.CircleStroke -> c.drawCircle(
            op.cx + dx, op.cy + dy, op.radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = op.strokePx; color = op.argb.toInt()
            }
        )
        is com.meteomontana.android.domain.model.DrawOp.TextLabel -> c.drawText(
            op.text, op.cx + dx, op.cy + dy + op.baselineOffsetPx,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = op.argb.toInt(); textAlign = Paint.Align.CENTER
                textSize = op.sizePx; isFakeBoldText = op.bold
            }
        )
    }
}

private fun wrapTextShare(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
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
