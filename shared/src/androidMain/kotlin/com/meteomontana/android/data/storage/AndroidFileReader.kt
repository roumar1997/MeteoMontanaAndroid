package com.meteomontana.android.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.port.FileReader
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidFileReader(
    private val context: Context
) : FileReader {

    override suspend fun readBytes(ref: FileRef): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(ref.path))?.use { it.readBytes() }
            ?: error("No se pudo leer el fichero: ${ref.path}")

    override suspend fun readImageCompressed(ref: FileRef, maxDim: Int, quality: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(ref.path)
            // Pasada 1: bounds para decidir inSampleSize.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val w = bounds.outWidth; val h = bounds.outHeight
            if (w <= 0 || h <= 0) error("Imagen ilegible: ${ref.path}")
            var sample = 1
            while ((w / (sample * 2)) >= maxDim && (h / (sample * 2)) >= maxDim) sample *= 2
            // Pasada 2: decode real.
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Imagen ilegible: ${ref.path}")
            // Rotación EXIF: la cámara guarda los píxeles "tumbados" y anota la
            // orientación en metadatos; BitmapFactory los ignora → las fotos
            // verticales subían en horizontal (bug visto en la foto de
            // celebración del feed; afectaba igual a perfil/notas). Al
            // recomprimir a JPEG el EXIF se pierde, así que hay que HORNEAR la
            // rotación en los píxeles antes de comprimir.
            val rotated = applyExifRotation(uri, decoded)
            // Reescalado fino al maxDim exacto si aún sigue siendo grande.
            val scale = (maxDim.toFloat() / maxOf(rotated.width, rotated.height)).coerceAtMost(1f)
            val finalBmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    rotated,
                    (rotated.width * scale).toInt(),
                    (rotated.height * scale).toInt(),
                    true
                )
            } else rotated
            // Compresión JPEG.
            val out = ByteArrayOutputStream()
            finalBmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (decoded !== rotated) decoded.recycle()
            if (rotated !== finalBmp) rotated.recycle()
            finalBmp.recycle()
            out.toByteArray()
        }

    /** Devuelve el bitmap rotado según el EXIF del fichero (o el mismo si no aplica). */
    private fun applyExifRotation(uri: Uri, bmp: Bitmap): Bitmap {
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Throwable) {
            0f // sin EXIF legible → se queda como está
        }
        if (degrees == 0f) return bmp
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }
}
