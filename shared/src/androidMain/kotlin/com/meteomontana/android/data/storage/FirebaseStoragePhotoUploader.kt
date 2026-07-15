package com.meteomontana.android.data.storage

import com.meteomontana.android.data.api.KtorPhotoApi
import com.meteomontana.android.domain.port.PhotoUploader

/**
 * Sube las fotos Tipo A al BACKEND (que las guarda en Cloudflare R2, egress
 * gratis) en vez de directas a Firebase. Mantiene el downscale a 2048 px antes
 * de subir. El nombre de la clase queda por compatibilidad de wiring; ya no usa
 * Firebase Storage.
 */
class FirebaseStoragePhotoUploader(
    private val photoApi: KtorPhotoApi
) : PhotoUploader {

    /**
     * Reduce el JPEG a máx 2048 px de lado largo. Sin esto, una foto del móvil a
     * resolución completa (12MP, varios MB) supera el límite de tamaño de las
     * reglas de Storage y la subida se DENIEGA (fallaba la 2ª foto de una piedra
     * multi-cara aunque hubiera cobertura). Con fallo de decodificado, deja los
     * bytes originales.
     */
    private fun downscaleJpeg(bytes: ByteArray, maxDim: Int = 2048, quality: Int = 85): ByteArray {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longSide = maxOf(bounds.outWidth, bounds.outHeight)
            if (longSide <= maxDim) return bytes
            var sample = 1
            while (longSide / (sample * 2) >= maxDim) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return bytes
            val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            val scaled = if (scale < 1f)
                android.graphics.Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1), true)
            else bmp
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (_: Throwable) { bytes }
    }

    override suspend fun uploadBoulderPhoto(bytes: ByteArray, mimeType: String, schoolId: String): String =
        photoApi.upload("boulder", downscaleJpeg(bytes), schoolId = schoolId)

    override suspend fun uploadNotePhoto(bytes: ByteArray, mimeType: String, schoolId: String): String =
        photoApi.upload("note", downscaleJpeg(bytes), schoolId = schoolId)

    override suspend fun uploadMeetupPhoto(bytes: ByteArray, mimeType: String, meetupId: String): String =
        photoApi.upload("meetup", downscaleJpeg(bytes), meetupId = meetupId)

    override suspend fun uploadProfilePhoto(bytes: ByteArray, mimeType: String): String =
        photoApi.upload("profile", bytes)   // el perfil ya llega comprimido del picker
}
