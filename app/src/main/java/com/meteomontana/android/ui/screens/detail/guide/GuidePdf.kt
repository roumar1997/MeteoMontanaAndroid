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

/**
 * La GUÍA de una escuela en PDF (A4), generada en el móvil. Espejo de `GuidePDF.swift` (iOS) con el diseño que eligió
 * Álvaro (2026-11-03): portada OSCURA con el satélite entero, el nombre en grande, las cifras y un ÍNDICE de sectores
 * que se puede tocar; páginas CLARAS con la marca Cumbre y el sector arriba, botones de volver abajo, y cada cara de
 * cada piedra en una tarjeta: foto con sus líneas y sus vías numeradas (sin casillas de marcar).
 *
 * Sale con los MISMOS filtros que la guía en pantalla y funciona sin conexión (fotos de la caché de Coil).
 */
@Singleton
class GuidePdf @Inject constructor(@ApplicationContext private val context: Context) {

    /** Genera el PDF y devuelve el fichero. [onlySector] = solo ese sector (su `key`); null = toda la guía. */
    suspend fun make(
        schoolName: String, sectors: List<GuideSector>, overview: GuideOverview?,
        sectorMaps: Map<String, Bitmap>, onlySector: String?
    ): File? {
        val chosen = sectors.filter { onlySector == null || it.key == onlySector }
        if (chosen.isEmpty()) return null

        // Las fotos primero (red o caché); luego se dibuja todo de una vez.
        val photos = HashMap<String, Bitmap>()
        for (s in chosen) for (b in s.blocks) for (f in b.faces) {
            val p = f.photo ?: continue
            if (p !in photos) loadPhoto(p)?.let { photos[p] = compact(it, PHOTO_PX).also { _ -> it.recycle() } }
        }

        return withContext(Dispatchers.Default) {
            val fonts = Fonts(context)
            val input = GuidePdfWriter.Input(
                school = schoolName, sectors = chosen,
                overview = overview?.let { it.copy(image = compact(it.image, 1400)) },
                photos = photos, sectorMaps = sectorMaps.mapValues { compact(it.value, 1100) }
            )
            // 1ª pasada en vacío (sin dibujar en ningún PDF): en qué página empieza cada sector y cuántas hay.
            val scratch = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val plan = GuidePdfWriter(input, fonts, known = null, newPage = { Canvas(scratch) }, endPage = {}).run()

            val doc = PdfDocument()
            var page: PdfDocument.Page? = null
            val written = GuidePdfWriter(input, fonts, known = plan,
                newPage = { n ->
                    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), n).create())
                    page!!.canvas
                },
                endPage = { page?.let { doc.finishPage(it) }; page = null }
            ).run()
            // Al disco y de vuelta en UNA sola copia: un ByteArrayOutputStream crece a saltos y luego se copia entero, y con
            // ~65 MB de PDF sin comprimir eso pasaba de los 256 MB que Android deja a la app (el arreglo fallaba en silencio).
            val tmp = File(context.cacheDir, "guide-raw.pdf")
            val raw = try {
                tmp.outputStream().use { doc.writeTo(it) }
                doc.close()
                tmp.readBytes()
            } finally { tmp.delete() }
            val finished = PdfFinisher.finish(raw, links(written), PAGE_H, ::jpeg)

            val safe = schoolName.replace("/", "-")
            val suffix = onlySector?.let { key -> " - " + (chosen.firstOrNull()?.sector ?: key) } ?: ""
            val dir = File(context.cacheDir, "guide").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }   // solo la última: no se acumulan PDFs en la caché
            val file = File(dir, AppText.get(R.string.sguide_pdf_file, safe, suffix) + ".pdf")
            runCatching { file.writeBytes(finished) }.getOrNull() ?: return@withContext null
            file
        }
    }

    /**
     * Los enlaces internos. La barra de arriba del visor TAPA lo que cae debajo: se salta 170 pt por encima de la
     * tarjeta; si eso cae antes del principio de la página, el punto va al final de la página anterior (como iOS).
     */
    private fun links(plan: GuidePdfWriter.Plan): List<PdfFinisher.Link> = plan.links.mapNotNull { l ->
        val (toPage, y) = plan.dests[l.dest] ?: return@mapNotNull null
        val want = y - 170f
        val (page, top) = if (want >= 0f || toPage < 2) toPage to max(want, 0f) else (toPage - 1) to (PAGE_H + want)
        PdfFinisher.Link(l.page - 1, l.rect.left, l.rect.top, l.rect.right, l.rect.bottom, page - 1, top)
    }

    private suspend fun loadPhoto(url: String): Bitmap? {
        val req = ImageRequest.Builder(context).data(url).allowHardware(false)
            // Reducida AL DECODIFICAR y sin guardarla en la caché de memoria: una foto de 12 Mpx a tamaño completo son
            // ~48 MB, y con todas las de la escuela el móvil entero se quedaba sin memoria y se colgaba.
            .size(PHOTO_PX).memoryCachePolicy(coil.request.CachePolicy.DISABLED).build()
        val r = context.imageLoader.execute(req) as? SuccessResult ?: return null
        return (r.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
    }

    /** Las fotos del PDF van en JPEG de calidad 70 (las guarda sin comprimir Android y luego se cambian). */
    private fun jpeg(w: Int, h: Int, rgb: ByteArray): ByteArray? = runCatching {
        val px = IntArray(w * h) { i ->
            val k = i * 3
            (0xFF shl 24) or ((rgb[k].toInt() and 0xFF) shl 16) or ((rgb[k + 1].toInt() and 0xFF) shl 8) or
                (rgb[k + 2].toInt() and 0xFF)
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 70, out); bmp.recycle(); out.toByteArray() }
    }.getOrNull()

    companion object {
        const val PAGE_W = 595f
        const val PAGE_H = 842f
        /** Lado mayor de cada foto en el PDF (la página mide 595 pt de ancho: de sobra para verla nítida). */
        const val PHOTO_PX = 900

        /** Reducida a [maxSide] px y OPACA (sin canal alfa: así Android la guarda como una sola imagen RGB). */
        fun compact(img: Bitmap, maxSide: Int): Bitmap {
            val scale = min(1f, maxSide.toFloat() / max(img.width, img.height).coerceAtLeast(1))
            val w = max(1, (img.width * scale).toInt()); val h = max(1, (img.height * scale).toInt())
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawColor(0xFFFFFFFF.toInt())
                drawBitmap(img, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            out.setHasAlpha(false)
            return out
        }
    }
}

/** Tipos de letra del PDF: los mismos `.ttf` que la app (y que iOS). */
internal class Fonts(ctx: Context) {
    val serif: Typeface = ResourcesCompat.getFont(ctx, R.font.source_serif4_bold) ?: Typeface.SERIF
    val monoBold: Typeface = ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_bold) ?: Typeface.MONOSPACE
    val mono: Typeface = ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
    private val inter: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_variable) ?: Typeface.DEFAULT

    /** Inter con su peso (600 = semibold); en Android < 9 solo hay normal y negrita. */
    fun sans(weight: Int, italic: Boolean = false): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(inter, weight, italic)
        else Typeface.create(inter, (if (weight >= 600) Typeface.BOLD else Typeface.NORMAL) or (if (italic) Typeface.ITALIC else 0))
}

