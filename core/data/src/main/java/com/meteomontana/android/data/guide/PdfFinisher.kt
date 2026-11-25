package com.meteomontana.android.data.guide

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.Inflater

/**
 * Remata un PDF escrito con `android.graphics.pdf.PdfDocument` (Skia), que se queda corto en dos cosas:
 *
 *  1. **No hace enlaces internos** (índice → sector, vía → su piedra). Se añaden como anotaciones `/Link`.
 *  2. **Guarda las fotos SIN comprimir** (píxeles en bruto con Flate): una guía con 100 fotos pasaba de 100 MB, igual
 *     que le pasó a iOS. Cada imagen RGB se cambia por un JPEG (lo hace [jpeg], que vive en la capa Android).
 *
 * Reescribe el fichero entero (copiando tal cual todo lo demás) con una tabla de objetos nueva. Kotlin puro para poder
 * probarlo en la JVM. Si no entiende el PDF lo devuelve como estaba: mejor sin enlaces que roto.
 */
object PdfFinisher {

    /**
     * Un enlace en la página [fromPage] (0 = primera) que ocupa [left]..[right] × [top]..[bottom] en puntos, contando
     * desde ARRIBA (como al dibujar). Lleva a la página [toPage] con su borde de arriba en [toTop] (también desde arriba).
     */
    data class Link(
        val fromPage: Int, val left: Float, val top: Float, val right: Float, val bottom: Float,
        val toPage: Int, val toTop: Float
    )

    /** Codifica a JPEG una imagen de [width]×[height] en RGB de 8 bits (3 bytes por píxel). null = dejarla como está. */
    fun interface JpegEncoder { fun encode(width: Int, height: Int, rgb: ByteArray): ByteArray? }

    fun finish(pdf: ByteArray, links: List<Link>, pageHeight: Float, jpeg: JpegEncoder?): ByteArray =
        runCatching { finishOrThrow(pdf, links, pageHeight, jpeg) }
            .onFailure { System.err.println("PdfFinisher: se deja el PDF sin enlaces ni JPEG: $it") }
            .getOrDefault(pdf)

    internal fun finishOrThrow(pdf: ByteArray, links: List<Link>, pageHeight: Float, jpeg: JpegEncoder?): ByteArray {
        // ISO-8859-1: un carácter por byte, así las posiciones del texto son posiciones del fichero.
        val text = Latin1(pdf)
        val trailer = text.substring(text.lastIndexOf("trailer"))
        val size = Regex("/Size\\s+(\\d+)").find(trailer)?.groupValues?.get(1)?.toInt() ?: error("trailer incompleto")
        val root = Regex("/Root\\s+(\\d+)\\s+0\\s+R").find(trailer)?.groupValues?.get(1)?.toInt() ?: error("trailer incompleto")
        val info = Regex("/Info\\s+(\\d+)\\s+0\\s+R").find(trailer)?.groupValues?.get(1)?.toInt()

        // Dónde empieza cada objeto (de la tabla xref) y su trozo de fichero completo.
        val objects = (1 until size).associateWith { id -> objectRange(text, id) }

        val pagesRoot = ref(dict(text, objects.getValue(root)), "/Pages") ?: error("sin /Pages")
        val pages = mutableListOf<Int>()
        collectPages(text, objects, pagesRoot, pages, depth = 0)

        // Anotaciones nuevas, por página.
        var next = size
        val annots = LinkedHashMap<Int, String>()            // id → cuerpo del objeto
        val annotsOfPage = HashMap<Int, MutableList<Int>>()  // página (id) → sus anotaciones
        for (l in links) {
            if (l.fromPage !in pages.indices || l.toPage !in pages.indices) continue
            val id = next++
            val y1 = pageHeight - l.bottom
            val y2 = pageHeight - l.top
            annots[id] = "<</Type /Annot /Subtype /Link /Rect [${n(l.left)} ${n(y1)} ${n(l.right)} ${n(y2)}]" +
                " /Border [0 0 0] /Dest [${pages[l.toPage]} 0 R /XYZ 0 ${n(pageHeight - l.toTop)} null]>>"
            annotsOfPage.getOrPut(pages[l.fromPage]) { mutableListOf() }.add(id)
        }

        val out = ByteArrayOutputStream(pdf.size / 4)   // con las fotos en JPEG queda en ~1/4
        val headerEnd = objects.values.minOf { it.first }
        out.write(pdf, 0, headerEnd)
        val offsets = sortedMapOf<Int, Int>()
        for (id in 1 until size) {
            val range = objects.getValue(id)
            offsets[id] = out.size()
            val ann = annotsOfPage[id]
            val replaced = when {
                ann != null -> {
                    val d = dict(text, range)
                    require(!d.contains("/Annots")) { "la página ya tiene /Annots" }
                    ("$id 0 obj\n" + d.dropLast(2) + " /Annots [${ann.joinToString(" ") { "$it 0 R" }}]>>\nendobj\n")
                        .toByteArray(Charsets.ISO_8859_1)
                }
                jpeg != null -> recompressImage(pdf, text, id, range, jpeg)
                else -> null
            }
            if (replaced != null) out.write(replaced) else out.write(pdf, range.first, range.last - range.first + 1)
        }
        for ((id, body) in annots) {
            offsets[id] = out.size()
            out.write("$id 0 obj\n$body\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        val xref = out.size()
        val sb = StringBuilder("xref\n0 $next\n0000000000 65535 f \n")
        for (id in 1 until next) sb.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.getValue(id)))
        sb.append("trailer\n<</Size $next /Root $root 0 R")
        if (info != null) sb.append(" /Info $info 0 R")
        sb.append(">>\nstartxref\n$xref\n%%EOF\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    /**
     * Una imagen RGB de 8 bits comprimida con Flate (lo que escribe Skia para una foto) pasada a JPEG. null si el
     * objeto no es eso exactamente (con máscara, otro color, otro filtro…): entonces se copia tal cual.
     */
    private fun recompressImage(pdf: ByteArray, text: CharSequence, id: Int, range: IntRange, jpeg: JpegEncoder): ByteArray? {
        if (!startsWithDict(text, text.indexOf("obj", range.first) + 3)) return null
        val d = dict(text, range)
        if (!d.contains("/Subtype /Image") || !d.contains("/ColorSpace /DeviceRGB") ||
            !Regex("/BitsPerComponent\\s+8").containsMatchIn(d) || !d.contains("/Filter /FlateDecode") ||
            d.contains("/SMask") || d.contains("/DecodeParms") || d.contains("/Mask")
        ) return null
        val w = Regex("/Width\\s+(\\d+)").find(d)?.groupValues?.get(1)?.toInt() ?: return null
        val h = Regex("/Height\\s+(\\d+)").find(d)?.groupValues?.get(1)?.toInt() ?: return null
        val len = Regex("/Length\\s+(\\d+)(?!\\s+0\\s+R)").find(d)?.groupValues?.get(1)?.toInt() ?: return null
        val dataStart = streamStart(text, range) ?: return null
        val raw = inflate(pdf, dataStart, len, w * h * 3) ?: return null
        val encoded = jpeg.encode(w, h, raw) ?: return null
        if (encoded.size >= len) return null   // no gana nada: se queda la original
        val head = "$id 0 obj\n<</Type /XObject /Subtype /Image /Width $w /Height $h /ColorSpace /DeviceRGB" +
            " /BitsPerComponent 8 /Filter /DCTDecode /Length ${encoded.size}>>\nstream\n"
        return head.toByteArray(Charsets.ISO_8859_1) + encoded + "\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1)
    }

    private fun inflate(pdf: ByteArray, start: Int, len: Int, expected: Int): ByteArray? {
        if (start + len > pdf.size) return null
        val inf = Inflater()
        return try {
            inf.setInput(pdf, start, len)
            val out = ByteArray(expected)
            var got = 0
            while (got < expected && !inf.finished()) {
                val k = inf.inflate(out, got, expected - got)
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break
                got += k
            }
            if (got == expected) out else null
        } catch (_: Exception) {
            null
        } finally {
            inf.end()
        }
    }

    /** Dónde empiezan los datos del stream del objeto (tras «stream» y su salto de línea). */
    private fun streamStart(text: CharSequence, range: IntRange): Int? {
        val dictEnd = dictRange(text, range).last + 1
        val k = text.indexOf("stream", dictEnd).takeIf { it in dictEnd..range.last } ?: return null
        var i = k + "stream".length
        if (text[i] == '\r') i++
        if (text[i] == '\n') i++
        return i
    }

    /** El trozo del fichero del objeto [id]: desde «id 0 obj» hasta «endobj» y su salto de línea. */
    private fun objectRange(text: CharSequence, id: Int): IntRange {
        val start = xrefOffset(text, id)
        require(text.startsWith("$id 0 obj", start)) { "el objeto $id no está donde dice la tabla" }
        val afterObj = start + "$id 0 obj".length
        var searchFrom = afterObj
        if (startsWithDict(text, afterObj)) {
            // Con stream, «endobj» va después de los datos: se salta el stream por su longitud para no confundirse
            // con bytes de la foto que por casualidad digan «endobj».
            val dEnd = dictRangeFrom(text, start).last + 1
            searchFrom = dEnd
            val streamKw = findIn(text, Regex("\\s*stream\\r?\\n"), dEnd)
            if (streamKw != null && streamKw.first == dEnd) {
                val d = text.substring(afterObj, dEnd)
                val len = Regex("/Length\\s+(\\d+)(?!\\s+0\\s+R)").find(d)?.groupValues?.get(1)?.toInt()
                    ?: error("stream sin /Length directa en $id")
                searchFrom = streamKw.last + 1 + len
            }
        }
        val end = text.indexOf("endobj", searchFrom)
        require(end > 0) { "objeto $id sin endobj" }
        var last = end + "endobj".length
        while (last < text.length && (text[last] == '\n' || text[last] == '\r')) last++
        return start until last
    }

    private fun xrefOffset(text: CharSequence, id: Int): Int {
        val x = text.lastIndexOf("\nxref") + 1
        val m = Regex("xref\\s+(\\d+)\\s+(\\d+)\\s*\\n").find(text.substring(x, minOf(text.length, x + 64)))
            ?: error("sin xref")
        val first = m.groupValues[1].toInt()
        val entry = x + m.range.last + 1 + (id - first) * 20
        return text.substring(entry, entry + 10).toInt()
    }

    /** Recorre el árbol de páginas (Skia lo parte en nodos de pocas hojas) y apunta las páginas en orden. */
    private fun collectPages(text: CharSequence, objects: Map<Int, IntRange>, id: Int, out: MutableList<Int>, depth: Int) {
        require(depth < 32) { "árbol de páginas demasiado hondo" }
        val d = dict(text, objects.getValue(id))
        if (Regex("/Type\\s*/Page(?![a-zA-Z])").containsMatchIn(d)) { out.add(id); return }
        val kids = Regex("/Kids\\s*\\[([^\\]]*)]").find(d)?.groupValues?.get(1) ?: return
        Regex("(\\d+)\\s+0\\s+R").findAll(kids)
            .forEach { collectPages(text, objects, it.groupValues[1].toInt(), out, depth + 1) }
    }

    /** ¿Lo primero tras «obj» es un diccionario `<<`? (hay objetos que son un número o una lista). */
    private fun startsWithDict(text: CharSequence, from: Int): Boolean {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return text.startsWith("<<", i)
    }

    private fun dict(text: CharSequence, range: IntRange): String {
        require(startsWithDict(text, text.indexOf("obj", range.first) + 3)) { "no es un diccionario" }
        return text.substring(dictRange(text, range))
    }

    private fun dictRange(text: CharSequence, range: IntRange): IntRange = dictRangeFrom(text, range.first)

    /** El diccionario `<<…>>` que sigue a «id 0 obj» en [objStart] (contando los anidados). */
    private fun dictRangeFrom(text: CharSequence, objStart: Int): IntRange {
        val start = text.indexOf("<<", objStart)
        var depth = 0
        var i = start
        while (i < text.length - 1) {
            if (text[i] == '<' && text[i + 1] == '<') { depth++; i += 2; continue }
            if (text[i] == '>' && text[i + 1] == '>') {
                depth--; i += 2
                if (depth == 0) return start until i
                continue
            }
            i++
        }
        error("diccionario sin cerrar")
    }

    private fun ref(dict: String, key: String): Int? =
        Regex("${Regex.escape(key)}\\s+(\\d+)\\s+0\\s+R").find(dict)?.groupValues?.get(1)?.toInt()

    private fun n(v: Float) = String.format(Locale.ROOT, "%.2f", v)
}

/**
 * El PDF visto como texto SIN copiarlo: un byte = un carácter (ISO-8859-1). Antes se hacía `String(pdf)`, que en
 * Android ocupa el DOBLE que el fichero (2 bytes por carácter); con una escuela grande eso eran cientos de MB.
 */
private class Latin1(private val bytes: ByteArray) : CharSequence {
    override val length: Int get() = bytes.size
    override fun get(index: Int): Char = (bytes[index].toInt() and 0xFF).toChar()
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        String(bytes, startIndex, endIndex - startIndex, Charsets.ISO_8859_1)
    override fun toString(): String = String(bytes, Charsets.ISO_8859_1)
}

/**
 * Busca [re] en una VENTANA pequeña de [text] desde [from] (las coincidencias que buscamos son cortas). En Android,
 * `Regex.find` sobre un CharSequence lo convierte ENTERO en String: con un PDF de 65 MB eso pedía 131 MB y fallaba.
 * Devuelve el rango en posiciones del texto completo.
 */
private fun findIn(text: CharSequence, re: Regex, from: Int, window: Int = 64): IntRange? {
    val end = minOf(text.length, from + window)
    if (from >= end) return null
    val r = re.find(text.subSequence(from, end).toString())?.range ?: return null
    return (r.first + from)..(r.last + from)
}
