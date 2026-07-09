package com.meteomontana.android.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.DrawOp
import com.meteomontana.android.domain.util.TopoLineData
import com.meteomontana.android.domain.util.gradeArgb
import com.meteomontana.android.domain.util.renderTopo
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import java.io.File

/* ── Paleta Cumbre (ARGB para Canvas, = ShareConditionsImage) ──────────────── */
private const val PAPER = 0xFFFAF7F2.toInt()
private const val INK = 0xFF1A1A1A.toInt()
private const val INK_SOFT = 0xFF6B6B6B.toInt()
private const val RULE = 0xFFE2DCD2.toInt()
private const val TERRA = 0xFFC0532B.toInt()

private const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.meteomontana.android"
private const val APPSTORE_URL = "https://apps.apple.com/app/id6785776686"

/**
 * Genera una imagen vertical (1080×1920, formato historia) con la FOTO real de
 * la vía y sus líneas dibujadas encima (idéntico a como se ven en la app),
 * más cabecera Cumbre con nombre/grado/piedra·escuela, y abre el share sheet.
 *
 * En el menú aparece Instagram (→ Historia), WhatsApp, etc., con la imagen ya
 * adjunta — igual de fluido que el compartir de escuelas.
 *
 * Es `suspend` porque hay que descargar la foto (Coil) antes de pintar. Si la
 * vía no tiene foto o dibujo, devuelve `false` y el caller cae al texto.
 *
 * @return true si compartió la imagen; false si no había foto/dibujo (fallback).
 */
suspend fun shareLineAsImage(
    context: Context,
    block: Block,
    line: BlockLine,
    schoolName: String
): Boolean {
    // 1. Localiza la cara (foto + líneas) a la que pertenece esta vía.
    val face = block.facesOrDerived().firstOrNull { f -> f.lines.any { it.id == line.id } }
    val photoUrl = face?.photoPath ?: line.photoPath ?: block.photoPath
    if (photoUrl.isNullOrBlank()) return false
    // Líneas a pintar: todas las de esa cara (contexto completo). Si por lo que
    // sea la cara no agrupó, al menos pinta la vía compartida.
    val linesToDraw = (face?.lines ?: listOf(line)).filter {
        parseLineStroke(it.linePath).points.isNotEmpty()
    }
    if (linesToDraw.isEmpty()) return false

    // 2. Descarga la foto a un Bitmap software (allowHardware=false → dibujable).
    val request = ImageRequest.Builder(context)
        .data(photoUrl)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request)
    val photoBmp = (result as? SuccessResult)?.drawable?.toBitmap() ?: return false

    // 3. Compón la imagen y compártela.
    val bmp = renderLineCard(block, line, schoolName, linesToDraw, photoBmp)
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "via.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val kind = if (block.discipline.equals("ROUTE", ignoreCase = true)) "vía" else "bloque"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(
            Intent.EXTRA_TEXT,
            "🧗 ${line.name} en Cumbre\n\nDescarga Cumbre:\nAndroid: $PLAY_URL\niOS: $APPSTORE_URL"
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir $kind"))
    return true
}

/** topoAspectRatio de TopoPhotoCanvas: recorta el ratio a un rango razonable. */
private fun topoAspectRatio(w: Int, h: Int): Float =
    if (w <= 0 || h <= 0) 4f / 3f else (w.toFloat() / h).coerceIn(0.55f, 2.2f)

private fun renderLineCard(
    block: Block,
    line: BlockLine,
    schoolName: String,
    lines: List<BlockLine>,
    photo: Bitmap
): Bitmap {
    val w = 1080
    val h = 1920
    val pad = 72f
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(PAPER)

    // Borde regla fina, como las cards de la app.
    c.drawRect(
        16f, 16f, w - 16f, h - 16f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = RULE
        }
    )

    // ── Cabecera ───────────────────────────────────────────────────────────
    val kind = if (block.discipline.equals("ROUTE", ignoreCase = true)) "VÍA" else "BLOQUE"
    c.drawText(
        "$kind EN CUMBRE", pad, pad + 40f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TERRA; textSize = 34f; typeface = Typeface.MONOSPACE
            letterSpacing = 0.18f; isFakeBoldText = true
        }
    )

    // Nombre de la vía (serif grande, hasta 2 líneas).
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; textSize = 88f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val nameLines = wrapText(line.name.ifBlank { block.name }, titlePaint, w - 2 * pad, maxLines = 2)
    var y = pad + 150f
    nameLines.forEach { l ->
        c.drawText(l, pad, y, titlePaint)
        y += 96f
    }

    // Grado (badge coloreado) + piedra · escuela.
    val grade = line.grade?.takeIf { it.isNotBlank() }
    var subX = pad
    if (grade != null) {
        val (gArgb, _, gDark) = gradeArgb(grade)
        val gPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 40f; isFakeBoldText = true
        }
        val gw = gPaint.measureText(grade)
        val badge = RectF(subX, y - 42f, subX + gw + 40f, y + 12f)
        c.drawRoundRect(badge, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gArgb.toInt() })
        gPaint.color = if (gDark) INK else Color.WHITE
        gPaint.textAlign = Paint.Align.CENTER
        c.drawText(grade, badge.centerX(), y - 2f, gPaint)
        subX = badge.right + 20f
    }
    val where = buildString {
        append(block.name)
        if (schoolName.isNotBlank()) append(" · ").append(schoolName)
    }
    c.drawText(
        where, subX, y - 6f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_SOFT; textSize = 38f }
    )
    y += 40f

    // ── Foto con las líneas ─────────────────────────────────────────────────
    val footerH = 130f
    val av2 = h - footerH - y - pad          // alto disponible para la foto
    val availW = w - 2 * pad
    val ratio = topoAspectRatio(photo.width, photo.height)  // ancho/alto
    // Encaja la foto en el hueco respetando su ratio.
    var rectW = availW
    var rectH = rectW / ratio
    if (rectH > av2) {
        rectH = av2
        rectW = rectH * ratio
    }
    val left = (w - rectW) / 2f
    val top = y + (av2 - rectH) / 2f
    val dst = RectF(left, top, left + rectW, top + rectH)

    // Foto en modo centerCrop dentro de dst (igual que ContentScale.Crop).
    val src = centerCropSrc(photo.width, photo.height, rectW / rectH)
    c.save()
    c.clipRect(dst)
    c.drawBitmap(photo, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))

    // Líneas encima: renderTopo con badges escalados al tamaño de la foto.
    val s = rectW / 380f   // 380f ≈ ancho típico del Canvas en la app (dp)
    val topoLines = lines.map { bl ->
        TopoLineData(
            name = bl.name,
            grade = bl.grade,
            startType = bl.startType,
            points = parseLineStroke(bl.linePath).points.map { it.x to it.y },
            strokeWidthPx = 5f * s
        )
    }
    val ops = renderTopo(
        topoLines, rectW, rectH,
        badgeR = (14f * s) to (11f * s),
        badgeTextPx = (22f * s) to (7f * s),
        startR = (22f * s) to (18f * s),
        startTextPx = (18f * s) to (6f * s)
    )
    ops.forEach { drawOpNative(it, c, dst.left, dst.top) }
    c.restore()
    // Borde fino alrededor de la foto.
    c.drawRect(dst, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = RULE
    })

    // ── Pie de marca ────────────────────────────────────────────────────────
    c.drawText(
        "⛰ CUMBRE", w - pad, h - 56f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TERRA; textSize = 34f; typeface = Typeface.MONOSPACE
            letterSpacing = 0.18f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
    )

    return bmp
}

/** Rect origen para dibujar `photo` recortada (centerCrop) a un ratio dado. */
private fun centerCropSrc(pw: Int, ph: Int, dstRatio: Float): Rect {
    val photoRatio = pw.toFloat() / ph
    return if (photoRatio > dstRatio) {
        // Foto más ancha: recorta a los lados.
        val cropW = (ph * dstRatio).toInt()
        val x = (pw - cropW) / 2
        Rect(x, 0, x + cropW, ph)
    } else {
        // Foto más alta: recorta arriba/abajo.
        val cropH = (pw / dstRatio).toInt()
        val yOff = (ph - cropH) / 2
        Rect(0, yOff, pw, yOff + cropH)
    }
}

/** Ejecuta un DrawOp de renderTopo sobre un Canvas nativo, desplazado (dx,dy). */
private fun drawOpNative(op: DrawOp, c: Canvas, dx: Float, dy: Float) {
    when (op) {
        is DrawOp.LinePath -> {
            val path = Path()
            op.pts.forEachIndexed { i, (x, y) ->
                if (i == 0) path.moveTo(x + dx, y + dy) else path.lineTo(x + dx, y + dy)
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = op.widthPx; color = op.argb.toInt()
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                if (op.dashed) pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f)
            }
            c.drawPath(path, p)
        }
        is DrawOp.FilledCircle -> c.drawCircle(
            op.cx + dx, op.cy + dy, op.radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = op.argb.toInt() }
        )
        is DrawOp.CircleStroke -> c.drawCircle(
            op.cx + dx, op.cy + dy, op.radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = op.strokePx; color = op.argb.toInt()
            }
        )
        is DrawOp.TextLabel -> c.drawText(
            op.text, op.cx + dx, op.cy + dy + op.baselineOffsetPx,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = op.argb.toInt(); textAlign = Paint.Align.CENTER
                textSize = op.sizePx; isFakeBoldText = op.bold
            }
        )
    }
}

/** Parte un texto en como mucho `maxLines` líneas que quepan en `maxWidth`. */
private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
    if (paint.measureText(text) <= maxWidth) return listOf(text)
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth) {
            current = candidate
        } else {
            if (current.isNotEmpty()) lines.add(current)
            current = word
            if (lines.size == maxLines - 1) break
        }
    }
    if (current.isNotEmpty() && lines.size < maxLines) lines.add(current)
    // Si sobró texto, añade elipsis a la última línea.
    if (lines.size == maxLines) {
        var last = lines[maxLines - 1]
        while (paint.measureText("$last…") > maxWidth && last.isNotEmpty()) {
            last = last.dropLast(1)
        }
        lines[maxLines - 1] = "$last…"
    }
    return lines
}
