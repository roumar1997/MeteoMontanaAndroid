package com.meteomontana.android.guide

import com.meteomontana.android.data.guide.PdfFinisher
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfFinisherTest {

    private val w = 40
    private val h = 30

    /**
     * Un PDF mínimo como los de Skia: catálogo → nodo de páginas → nodo intermedio → 2 páginas, una foto RGB
     * comprimida con Flate (como guarda Skia las fotos) y un objeto que es solo un número.
     */
    private fun samplePdf(): ByteArray {
        val rgb = ByteArray(w * h * 3) { (it % 251).toByte() }
        val deflater = Deflater().apply { setInput(rgb); finish() }
        val buf = ByteArray(rgb.size * 2)
        val zipped = buf.copyOf(deflater.deflate(buf))
        val out = ByteArrayOutputStream()
        val offs = mutableListOf<Int>()
        fun obj(s: String) { offs.add(out.size()); out.write(s.toByteArray(Charsets.ISO_8859_1)) }
        out.write("%PDF-1.4\n".toByteArray())
        obj("1 0 obj\n<</Type /Catalog /Pages 2 0 R>>\nendobj\n")
        obj("2 0 obj\n<</Type /Pages /Kids [3 0 R] /Count 2>>\nendobj\n")
        obj("3 0 obj\n<</Type /Pages /Parent 2 0 R /Kids [4 0 R 5 0 R] /Count 2>>\nendobj\n")
        obj("4 0 obj\n<</Type /Page /Parent 3 0 R /Resources <</XObject <</X1 6 0 R>>>> /MediaBox [0 0 595 842]>>\nendobj\n")
        obj("5 0 obj\n<</Type /Page /Parent 3 0 R /Resources <</ProcSet [/PDF]>> /MediaBox [0 0 595 842]>>\nendobj\n")
        offs.add(out.size())
        out.write(("6 0 obj\n<</Type /XObject /Subtype /Image /Width $w /Height $h /ColorSpace /DeviceRGB " +
            "/BitsPerComponent 8 /Filter /FlateDecode /Length ${zipped.size}>> stream\n").toByteArray(Charsets.ISO_8859_1))
        out.write(zipped)
        out.write("\nendstream\nendobj\n".toByteArray())
        obj("7 0 obj\n42\nendobj\n")
        val xref = out.size()
        val sb = StringBuilder("xref\n0 8\n0000000000 65535 f \n")
        offs.forEach { sb.append(String.format("%010d 00000 n \n", it)) }
        sb.append("trailer\n<</Size 8 /Root 1 0 R>>\nstartxref\n$xref\n%%EOF\n")
        out.write(sb.toString().toByteArray())
        return out.toByteArray()
    }

    private fun String.xrefIsValid() {
        val xrefAt = Regex("startxref\\s+(\\d+)").findAll(this).last().groupValues[1].toInt()
        val section = substring(xrefAt)
        Regex("(\\d{10}) 00000 n \\n").findAll(section).forEachIndexed { i, m ->
            assertTrue("objeto ${i + 1}", startsWith("${i + 1} 0 obj", m.groupValues[1].toInt()))
        }
    }

    @Test
    fun `adds a link annotation to the right page`() {
        val out = PdfFinisher.finishOrThrow(
            samplePdf(), listOf(PdfFinisher.Link(1, 10f, 20f, 110f, 40f, toPage = 0, toTop = 0f)), 842f, jpeg = null
        )
        val text = String(out, Charsets.ISO_8859_1)
        assertTrue(text.contains("8 0 obj\n<</Type /Annot /Subtype /Link /Rect [10.00 802.00 110.00 822.00]"))
        assertTrue(text.contains("/Dest [4 0 R /XYZ 0 842.00 null]"))
        assertTrue(text.contains("/MediaBox [0 0 595 842] /Annots [8 0 R]>>"))
        assertTrue(text.contains("/Size 9 /Root 1 0 R>>"))
        text.xrefIsValid()
    }

    @Test
    fun `swaps a raw photo for the jpeg`() {
        var seen: ByteArray? = null
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val out = PdfFinisher.finishOrThrow(samplePdf(), emptyList(), 842f) { width, height, rgb ->
            assertEquals(w, width); assertEquals(h, height)
            seen = rgb
            fakeJpeg
        }
        assertEquals(w * h * 3, seen!!.size)
        assertEquals((5 % 251).toByte(), seen!![5])
        val text = String(out, Charsets.ISO_8859_1)
        assertTrue(text.contains("/Filter /DCTDecode /Length 5>>"))
        assertFalse(text.contains("/FlateDecode"))
        assertTrue(text.contains("7 0 obj\n42\nendobj"))
        text.xrefIsValid()
    }

    @Test
    fun `an unreadable pdf comes back untouched`() {
        val junk = "no es un pdf".toByteArray()
        assertArrayEquals(junk, PdfFinisher.finish(junk, listOf(PdfFinisher.Link(0, 0f, 0f, 1f, 1f, 0, 0f)), 842f, null))
    }
}
