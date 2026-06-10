package com.meteomontana.android.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
            // Reescalado fino al maxDim exacto si aún sigue siendo grande.
            val scale = (maxDim.toFloat() / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
            val finalBmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt(),
                    (decoded.height * scale).toInt(),
                    true
                )
            } else decoded
            // Compresión JPEG.
            val out = ByteArrayOutputStream()
            finalBmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (finalBmp !== decoded) finalBmp.recycle()
            decoded.recycle()
            out.toByteArray()
        }
}
