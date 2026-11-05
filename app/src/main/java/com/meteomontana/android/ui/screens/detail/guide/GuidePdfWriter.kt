package com.meteomontana.android.ui.screens.detail.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.meteomontana.android.R
import com.meteomontana.android.data.guide.PdfFinisher
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.schools.GuideBlock
import com.meteomontana.android.domain.usecase.schools.GuideSector
import com.meteomontana.android.domain.util.gradeArgb
import com.meteomontana.android.domain.util.renderTopo
import com.meteomontana.android.ui.components.startTypeLabelRes
import com.meteomontana.android.ui.share.drawOpNative
import com.meteomontana.android.util.AppText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.ACCENT
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.BOTTOM
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.BOX
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.CHIP
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.CONTENT_W
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.DARK
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.DARK_CARD
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.DESC
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.H
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.LINE
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.MARGIN
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.MUTED
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.MUTED_DARK
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.PAPER
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.TERRA
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.W
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.WHITE
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.blockDest
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.bmpPaint
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.brandPill
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.centered
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.clipRound
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.fill
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.lineH
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.measure
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.pdfMeta
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.pdfSectorTitle
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.pill
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.pillSize
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.stroke
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.textBlock
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.tp
import com.meteomontana.android.ui.screens.detail.guide.GuidePdfKit.withAlpha


// ── El maquetador (una pasada = un PDF entero) ──────────────────────────────────────────────────────────────────

internal class GuidePdfWriter(
    val input: Input,
    val f: Fonts,
    val known: Plan?,
    val newPage: (Int) -> Canvas,
    val endPage: () -> Unit
) {
    class Input(
        val school: String,
        val sectors: List<GuideSector>,
        val overview: GuideOverview?,
        val photos: Map<String, Bitmap>,
        /** Foto aérea de cada sector (clave → imagen): la portada del sector. Sin ella, la foto de una piedra. */
        val sectorMaps: Map<String, Bitmap>
    )

    class Link(val page: Int, val rect: RectF, val dest: String)

    /** Lo que la 1ª pasada averigua para la 2ª. */
    class Plan(
        val sectorPage: Map<Int, Int>, val total: Int,
        val links: List<Link>, val dests: Map<String, Pair<Int, Float>>
    )

    private lateinit var c: Canvas
    private var pageNo = 0
    private var y = 0f
    private var currentSector = ""
    private var currentSectorIndex = 0
    private val sectorPage = HashMap<Int, Int>()
    private val links = mutableListOf<Link>()
    private val dests = HashMap<String, Pair<Int, Float>>()
    private fun link(dest: String, r: RectF) { links.add(Link(pageNo, RectF(r), dest)) }
    private fun dest(name: String, y: Float) { dests[name] = pageNo to y }

    fun run(): Plan {
        cover()
        input.sectors.forEachIndexed { i, s -> sector(s, i) }
        endPage()
        return Plan(sectorPage, pageNo, links, dests)
    }

    private fun beginPage() {
        if (pageNo > 0) endPage()
        pageNo += 1
        c = newPage(pageNo)
    }

    // ── Portada ──

    private fun cover() {
        beginPage()
        dest("indice", 0f)
        c.drawRect(0f, 0f, W, H, fill(DARK))

        // El satélite ENTERO, a todo lo ancho (sin recortar: se ven todos los parkings aunque estén lejos).
        var top = 64f
        input.overview?.let { ov ->
            val img = ov.image
            val h = min(W * img.height / max(img.width, 1), 470f)
            val w = h * img.width / max(img.height, 1)
            val r = RectF((W - w) / 2, 0f, (W - w) / 2 + w, h)
            c.drawBitmap(img, null, r, bmpPaint)
            for (s in ov.sectors) {
                val t = s.name.uppercase()
                val p = tp(6.5f, f.monoBold, DARK)
                val size = pillSize(t, p, 5f, 2.5f)
                c.pill(t, p, 0xE0FFFFFF.toInt(), r.left + s.x.toFloat() * r.width() - size.first / 2,
                    r.top + s.y.toFloat() * r.height() - size.second / 2, 5f, 2.5f)
            }
            top = h
        }
        c.brandPill(f, 20f, 20f, null, withAlpha(DARK, 0.85f))
        val tag = AppText.get(R.string.sguide_pdf_tag)
        val tagP = tp(8f, f.monoBold, DARK)
        c.pill(tag, tagP, PAPER, W - 20 - pillSize(tag, tagP).first, 20f)

        y = top + 24
        y += c.textBlock(input.school, tp(54f, f.serif, WHITE), MARGIN, y, CONTENT_W) + 16

        // Cifras en tres tarjetas.
        val stats = listOf(
            "${input.sectors.sumOf { it.lineCount }}" to AppText.get(R.string.sguide_pdf_lines),
            "${input.sectors.sumOf { it.blocks.size }}" to AppText.get(R.string.sguide_pdf_blocks),
            "${input.sectors.size}" to AppText.get(R.string.sguide_pdf_sectors)
        )
        val cardW = (CONTENT_W - 20) / 3
        stats.forEachIndexed { i, (n, label) ->
            val r = RectF(MARGIN + i * (cardW + 10), y, MARGIN + i * (cardW + 10) + cardW, y + 54)
            c.drawRoundRect(r, 14f, 14f, fill(DARK_CARD))
            c.textBlock(n, tp(24f, f.sans(700), WHITE), r.left + 14, r.top + 8, cardW - 20)
            c.textBlock(label, tp(7.5f, f.monoBold, MUTED_DARK), r.left + 14, r.top + 37, cardW - 20)
        }
        y += 54 + 20

        // Índice: cada sector es una píldora que SALTA a su página.
        c.textBlock(AppText.get(R.string.sguide_index), tp(8f, f.monoBold, ACCENT), MARGIN, y, CONTENT_W)
        y += 16
        var x = MARGIN
        val nameP = tp(9.5f, f.sans(600), DARK)
        val pageP = tp(8.5f, f.monoBold, TERRA)
        input.sectors.forEachIndexed { i, s ->
            val name = pdfSectorTitle(s)
            val pageText = "  p." + (known?.sectorPage?.get(i)?.toString() ?: "00")
            val tw = nameP.measureText(name) + pageP.measureText(pageText)
            val th = lineH(nameP)
            val sw = ceil(tw) + 20; val sh = ceil(th) + 10
            if (x + sw > W - MARGIN) { x = MARGIN; y += sh + 6 }
            val r = RectF(x, y, x + sw, y + sh)
            c.drawRoundRect(r, sh / 2, sh / 2, fill(PAPER))
            val base = r.top + 5 - nameP.ascent()
            c.drawText(name, r.left + 10, base, nameP)
            c.drawText(pageText, r.left + 10 + nameP.measureText(name), base, pageP)
            link("s$i", r)
            x += sw + 6
        }

        val date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date())
        val footP = tp(7f, f.mono, MUTED_DARK)
        c.drawText("CLIMBINGTEAMS.COM · ${date.uppercase()}", MARGIN, H - 26 - footP.ascent(), footP)
        val right = AppText.get(R.string.sguide_pdf_downloaded)
        c.drawText(right, W - MARGIN - footP.measureText(right) - 2, H - 26 - footP.ascent(), footP)
    }

    // ── Páginas claras ──

    /** Página nueva con cabecera (marca + sector) y pie (botones de volver · sector · página / total). */
    private fun lightPage(continuing: Boolean) {
        beginPage()
        c.drawRect(0f, 0f, W, H, fill(PAPER))
        link("indice", c.brandPill(f, MARGIN, 18f, input.school, DARK))   // la marca vuelve al índice

        val sp = tp(9.5f, f.sans(700), WHITE)
        val size = pillSize(currentSector, sp, 11f, 5f)
        val px = W - MARGIN - size.first
        link("s$currentSectorIndex", c.pill(currentSector, sp, TERRA, px, 18f, 11f, 5f))
        val label = AppText.get(if (continuing) R.string.sguide_pdf_still_in else R.string.sguide_pdf_sector)
        val lp = tp(7.5f, f.mono, MUTED)
        c.drawText(label, px - lp.measureText(label) - 8, 18 + (size.second - 10) / 2 - lp.ascent(), lp)

        // Botones de volver (en todas las páginas): al índice de la portada y al inicio del sector.
        val backY = H - 34
        val bp = tp(7.5f, f.monoBold, WHITE)
        val toIndex = c.pill(AppText.get(R.string.sguide_pdf_to_index), bp, DARK, MARGIN, backY, 10f, 5f)
        link("indice", toIndex)
        link("s$currentSectorIndex",
            c.pill(AppText.get(R.string.sguide_pdf_to_sector), bp, TERRA, toIndex.right + 6, backY, 10f, 5f))
        val total = known?.total?.toString() ?: "00"
        val foot = "${currentSector.uppercase()} · $pageNo / $total"
        val fp = tp(7f, f.monoBold, DARK)
        c.pill(foot, fp, CHIP, W - MARGIN - pillSize(foot, fp, 9f, 4f).first, H - 32, 9f, 4f)
        y = 56f
    }

    private fun sector(s: GuideSector, index: Int) {
        currentSector = pdfSectorTitle(s)
        currentSectorIndex = index
        lightPage(continuing = false)
        sectorPage[index] = pageNo
        dest("s$index", 0f)

        y += 8
        c.textBlock(String.format(Locale.ROOT, "%02d / %02d", index + 1, input.sectors.size), tp(9f, f.monoBold, TERRA), MARGIN, y, 120f)
        y += 14
        // Cifras del sector, a la derecha del título.
        val chips = listOf(
            AppText.get(R.string.sguide_blocks, s.blocks.size),
            AppText.get(R.string.sguide_lines, s.lineCount)
        )
        var cx = W - MARGIN
        val cp = tp(9.5f, f.sans(600), DARK)
        for (chip in chips.reversed()) {
            val size = pillSize(chip, cp, 10f, 6f)
            cx -= size.first
            c.pill(chip, cp, CHIP, cx, y + 10, 10f, 6f, radius = 10f)
            cx -= 6
        }
        y += c.textBlock(currentSector, tp(40f, f.serif, DARK), MARGIN, y, cx - MARGIN - 10) + 18

        sectorHero(s)
        sectorIndex(s)

        // Las piedras siguen en el hueco que deje el resumen: cada tarjeta salta de página solo si no cabe.
        y += 4
        for (b in s.blocks) {
            val faces = b.faces
            faces.forEachIndexed { fi, face -> faceCard(b, face, fi, faces.size) }
        }
    }

    /** Foto aérea del sector; si no hay, la foto de una piedra, ancha y recortada, sin líneas. */
    private fun sectorHero(s: GuideSector) {
        input.sectorMaps[s.key]?.let { map ->
            val r = RectF(MARGIN, y, MARGIN + CONTENT_W, y + CONTENT_W * map.height / max(map.width, 1))
            c.clipRound(r, 16f) { c.drawBitmap(map, null, r, bmpPaint) }
            y += r.height() + 20
            return
        }
        val img = s.blocks.asSequence().flatMap { it.faces }.mapNotNull { it.photo }
            .firstNotNullOfOrNull { input.photos[it] } ?: return
        val r = RectF(MARGIN, y, MARGIN + CONTENT_W, y + 190)
        c.clipRound(r, 16f) {
            // Rellena el marco (como "cover"): escala por el lado que más falte y centra.
            val scale = max(r.width() / img.width, r.height() / img.height)
            val w = img.width * scale; val h = img.height * scale
            c.drawBitmap(img, null, RectF(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2), bmpPaint)
        }
        y += r.height() + 20
    }

    /** TODAS las vías del sector por grado (de fácil a difícil); cada vía SALTA a la tarjeta de su piedra. */
    private fun sectorIndex(s: GuideSector) {
        c.textBlock(AppText.get(R.string.sguide_all_lines), tp(8f, f.monoBold, TERRA), MARGIN, y, CONTENT_W)
        y += 18
        val labelW = 50f
        val chipP = tp(9f, f.sans(600), DARK)
        val stoneP = tp(7.5f, f.mono, MUTED)
        for ((g, chips) in s.linesByGrade()) {
            val (argb, _, dark) = gradeArgb(g)
            val color = argb.toInt()
            // Altura del grupo (para no partirlo entre páginas si cabe entero en la siguiente).
            var x = MARGIN + labelW
            var rows = 1
            for (ch in chips) {
                val w = chipWidth(ch.hit.lineName ?: ch.block.blockName, ch.block.blockName, chipP, stoneP)
                if (x + w > W - MARGIN) { x = MARGIN + labelW; rows++ }
                x += w + 5
            }
            if (y + min(rows * 25f + 8, 120f) > BOTTOM) lightPage(continuing = true)

            // Grado: pastilla de su color a la izquierda.
            val gp = RectF(MARGIN, y + 1, MARGIN + 40, y + 21)
            c.drawRoundRect(gp, 8f, 8f, fill(if (dark) WHITE else color))
            if (dark) c.drawRoundRect(gp, 8f, 8f, stroke(BOX, 1.2f))
            c.centered(g, tp(10f, f.sans(700), if (dark) DARK else WHITE), gp)

            x = MARGIN + labelW
            for (ch in chips) {
                val name = ch.hit.lineName ?: ch.block.blockName
                val w = chipWidth(name, ch.block.blockName, chipP, stoneP)
                if (x + w > W - MARGIN) {
                    x = MARGIN + labelW; y += 25
                    if (y + 22 > BOTTOM) { lightPage(continuing = true); x = MARGIN + labelW }
                }
                val r = RectF(x, y, x + w, y + 22)
                c.drawRoundRect(r, 11f, 11f, fill(WHITE))
                c.drawRoundRect(r, 11f, 11f, stroke(LINE, 1f))
                val dot = RectF(r.left + 8, r.centerY() - 3.5f, r.left + 15, r.centerY() + 3.5f)
                c.drawOval(dot, fill(color))
                if (dark) c.drawOval(dot, stroke(BOX, 0.8f))
                val base = r.centerY() - (chipP.ascent() + chipP.descent()) / 2
                c.drawText(name, dot.right + 5, base, chipP)
                c.drawText(ch.block.blockName, dot.right + 5 + chipP.measureText(name) + 5,
                    r.centerY() - (stoneP.ascent() + stoneP.descent()) / 2, stoneP)
                link(blockDest(ch.block.blockId), r)
                x += w + 5
            }
            y += 25 + 8
        }
    }

    private fun chipWidth(name: String, stone: String, f1: Paint, f2: Paint) =
        ceil(8 + 7 + 5 + f1.measureText(name) + 5 + f2.measureText(stone) + 10)

    /** Página nueva a mitad de sector, recordando de qué piedra viene. */
    private fun continuePage(block: GuideBlock, startedAt: Int) {
        lightPage(continuing = true)
        y += 6
        c.drawOval(RectF(MARGIN, y + 4, MARGIN + 6, y + 10), fill(TERRA))
        c.textBlock(AppText.get(R.string.sguide_pdf_continued, block.blockName, startedAt), tp(10f, f.sans(400), MUTED),
            MARGIN + 12, y, CONTENT_W - 12)
        y += 22
    }

    // ── Tarjeta de una cara ──

    private val photoMaxH = 330f
    private val bandH = 40f
    private val rowTextW get() = CONTENT_W - 32 - 84
    private val nameP get() = tp(12f, f.sans(600), DARK)
    private val metaP get() = tp(9f, f.sans(400), MUTED)
    private val descP get() = tp(9.5f, f.sans(400, italic = true), DESC)

    private fun rowH(h: LineSearchHit, textW: Float = rowTextW): Float {
        var t = measure(h.lineName ?: h.blockName, nameP, textW)
        pdfMeta(h)?.let { t += 2 + measure(it, metaP, textW) }
        h.lineDescription?.takeIf { it.isNotEmpty() }?.let { t += 2 + measure(it, descP, textW) }
        return max(t, 20f) + 18
    }

    /** La foto de una cara con sus líneas encima, en el rectángulo [r]. */
    private fun drawPhoto(photo: Bitmap, face: GuideFace, r: RectF) {
        c.drawBitmap(photo, null, r, bmpPaint)
        val s = r.width() / 360f
        renderTopo(
            face.topoLines(3.5f * s), r.width(), r.height(),
            badgeR = 9f to 7.2f, badgeTextPx = 9.5f to 3.3f,
            startR = 10.5f to 8.5f, startTextPx = 7f to 2.5f,
            dashPx = 12f * s to 9f * s, stripePx = 22f * s,
            fanSpacingPx = (9f * 2 + 4) to (10.5f * 2 + 4)
        ).forEach { drawOpNative(it, c, r.left, r.top) }
    }

    /**
     * Foto VERTICAL: la foto a la izquierda (a su tamaño, sin franjas negras) y las vías a la derecha, para que
     * quepan dos piedras por página. Devuelve false si no cabe ni en una página entera (muchas vías): entonces
     * se usa la tarjeta normal, que sabe partirse. Igual que iOS (GuidePDF.sideCard).
     */
    private fun sideCard(b: GuideBlock, face: GuideFace, photo: Bitmap, faceIndex: Int, faceCount: Int): Boolean {
        val aspect = photo.height.toFloat() / max(photo.width, 1)
        val photoH = min(photoMaxH, CONTENT_W * 0.5f * aspect)
        val photoW = photoH / aspect
        val listX = MARGIN + photoW + 14
        val listW = CONTENT_W - photoW - 14 - 12
        val rows = face.lines.map { rowH(it, listW - 84) }
        val cardH = max(photoH, rows.sum() + 8)
        if (cardH > BOTTOM - 56 - 28) return false

        if (y + cardH > BOTTOM) continuePage(b, pageNo)
        if (faceIndex == 0) dest(blockDest(b.blockId), y)

        val card = RectF(MARGIN, y, MARGIN + CONTENT_W, y + cardH)
        c.drawRoundRect(card, 16f, 16f, fill(WHITE))
        val r = RectF(card.left, card.top, card.left + photoW, card.top + photoH)
        c.clipRound(card, 16f) { drawPhoto(photo, face, r) }
        val p1 = c.pill(AppText.get(R.string.sguide_stone, b.blockName.uppercase()), tp(8.5f, f.monoBold, WHITE),
            withAlpha(DARK, 0.88f), r.left + 10, r.top + 10)
        if (faceCount > 1) {
            c.pill(AppText.get(R.string.sguide_face_of, faceIndex + 1, faceCount), tp(8.5f, f.sans(600), DARK),
                0xE6FFFFFF.toInt(), r.left + 10, p1.bottom + 5)
        }
        var ry = card.top + 4
        face.lines.forEachIndexed { k, hit ->
            drawRow(hit, k + 1, ry, rows[k], last = k == rows.lastIndex, x0 = listX, width = listW)
            ry += rows[k]
        }
        y += cardH + 14
        return true
    }

    private fun faceCard(b: GuideBlock, face: GuideFace, faceIndex: Int, faceCount: Int) {
        val photo = face.photo?.let { input.photos[it] }
        if (photo != null && photo.height > photo.width * 1.05f && sideCard(b, face, photo, faceIndex, faceCount)) return
        val photoH = photo?.let { min(CONTENT_W * it.height / max(it.width, 1), photoMaxH) } ?: bandH
        val rows = face.lines.map(::rowH)
        val startPage = pageNo

        // Cabe la foto y al menos una vía; si no, página nueva.
        if (y + photoH + 12 + (rows.firstOrNull() ?: 0f) > BOTTOM) continuePage(b, startPage)
        // Aquí llegan los enlaces del listado del inicio del sector.
        if (faceIndex == 0) dest(blockDest(b.blockId), y)

        var idx = 0
        var withPhoto = true
        while (withPhoto || idx < rows.size) {
            val topH = if (withPhoto) photoH else bandH
            var h = topH + 8
            var n = 0
            while (idx + n < rows.size && y + h + rows[idx + n] + 4 <= BOTTOM) { h += rows[idx + n]; n++ }
            if (n == 0 && idx < rows.size) { n = 1; h += rows[idx] }   // siempre se avanza
            h += 4

            val card = RectF(MARGIN, y, MARGIN + CONTENT_W, y + h)
            c.drawRoundRect(card, 16f, 16f, fill(WHITE))

            // Cabeza: la foto con sus líneas (recortada a la tarjeta) o una banda clara.
            val head = RectF(card.left, card.top, card.right, card.top + topH)
            if (withPhoto && photo != null) {
                c.save()
                c.clipPath(Path().apply {
                    addRoundRect(head, floatArrayOf(16f, 16f, 16f, 16f, 0f, 0f, 0f, 0f), Path.Direction.CW)
                })
                c.drawRect(head, fill(DARK_CARD))
                val w = photoH * photo.width / max(photo.height, 1)
                val r = RectF(head.centerX() - w / 2, head.top, head.centerX() + w / 2, head.top + photoH)
                drawPhoto(photo, face, r)
                c.restore()
            }
            val p1 = c.pill(AppText.get(R.string.sguide_stone, b.blockName.uppercase()), tp(8.5f, f.monoBold, WHITE),
                withAlpha(DARK, 0.88f), head.left + 12, head.top + 10)
            if (faceCount > 1) {
                val cara = AppText.get(
                    if (withPhoto) R.string.sguide_face_of else R.string.sguide_face_of_cont, faceIndex + 1, faceCount
                )
                c.pill(cara, tp(8.5f, f.sans(600), DARK), 0xE6FFFFFF.toInt(), p1.right + 6, p1.top,
                    border = if (withPhoto) null else LINE)
            }

            var ry = card.top + topH + 4
            for (k in 0 until n) {
                drawRow(face.lines[idx + k], idx + k + 1, ry, rows[idx + k], last = k == n - 1)
                ry += rows[idx + k]
            }
            y += h + 14
            idx += n
            withPhoto = false
            if (idx < rows.size) continuePage(b, startPage) else break
        }
    }

    private fun drawRow(
        h: LineSearchHit, number: Int, ry: Float, height: Float, last: Boolean,
        x0: Float = MARGIN + 16, width: Float = CONTENT_W - 32
    ) {
        val innerX = x0
        val innerW = width
        val rowTextX = innerX + 84
        val rowTextW = innerW - 84
        val (argb, _, dark) = gradeArgb(h.grade)
        val color = argb.toInt()
        // Número (el mismo que la línea en la foto).
        val circle = RectF(innerX, ry + 9, innerX + 20, ry + 29)
        c.drawOval(circle, fill(color))
        if (dark) c.drawOval(RectF(circle).apply { inset(0.6f, 0.6f) }, stroke(DARK, 1.2f))
        c.centered("$number", tp(9.5f, f.sans(700), if (dark) DARK else WHITE), circle)
        // Grado.
        val g = RectF(innerX + 30, ry + 10, innerX + 74, ry + 28)
        c.drawRoundRect(g, 8f, 8f, fill(if (dark) WHITE else color))
        if (dark) c.drawRoundRect(g, 8f, 8f, stroke(BOX, 1.2f))
        c.centered(h.grade ?: "?", tp(9.5f, f.sans(700), if (dark) DARK else WHITE), g)
        // Nombre, salida/estrellas/repeticiones y comentario.
        var ty = ry + 9
        ty += c.textBlock(h.lineName ?: h.blockName, nameP, rowTextX, ty, rowTextW)
        pdfMeta(h)?.let { ty += 2 + c.textBlock(it, metaP, rowTextX, ty + 2, rowTextW) }
        h.lineDescription?.takeIf { it.isNotEmpty() }?.let { c.textBlock(it, descP, rowTextX, ty + 2, rowTextW) }
        if (!last) c.drawLine(innerX, ry + height - 0.5f, innerX + innerW, ry + height - 0.5f, stroke(LINE, 1f))
    }

}
