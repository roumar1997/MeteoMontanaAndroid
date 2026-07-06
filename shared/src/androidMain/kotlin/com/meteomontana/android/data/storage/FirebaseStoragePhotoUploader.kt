package com.meteomontana.android.data.storage

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.meteomontana.android.domain.port.PhotoUploader
import kotlinx.coroutines.tasks.await

class FirebaseStoragePhotoUploader(
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
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

    override suspend fun uploadBoulderPhoto(bytes: ByteArray, mimeType: String, schoolId: String): String {
        val uid = auth.currentUser?.uid ?: error("Usuario no autenticado")
        val ts = System.currentTimeMillis()
        val ref = storage.reference.child("piedra-photos-pending/${uid}_${schoolId}_${ts}.jpg")
        ref.putBytes(downscaleJpeg(bytes)).await()
        return ref.downloadUrl.await().toString()
    }

    override suspend fun uploadNotePhoto(bytes: ByteArray, mimeType: String, schoolId: String): String {
        val uid = auth.currentUser?.uid ?: error("Usuario no autenticado")
        val ts = System.currentTimeMillis()
        val ref = storage.reference.child("note-photos/${uid}_${schoolId}_${ts}.jpg")
        ref.putBytes(downscaleJpeg(bytes)).await()
        return ref.downloadUrl.await().toString()
    }

    override suspend fun uploadMeetupPhoto(bytes: ByteArray, mimeType: String, meetupId: String): String {
        val uid = auth.currentUser?.uid ?: error("Usuario no autenticado")
        val ts = System.currentTimeMillis()
        val ref = storage.reference.child("meetup-photos/${meetupId}_${uid}_${ts}.jpg")
        ref.putBytes(downscaleJpeg(bytes)).await()
        return ref.downloadUrl.await().toString()
    }

    override suspend fun uploadProfilePhoto(bytes: ByteArray, mimeType: String): String {
        val uid = auth.currentUser?.uid ?: error("Usuario no autenticado")
        val ext = when (mimeType) {
            "image/png"  -> "png"
            "image/webp" -> "webp"
            else         -> "jpg"
        }
        val path = "profile-photos/${uid}.${ext}"
        val log = co.touchlab.kermit.Logger.withTag("PhotoUploader")
        log.i("uploadProfilePhoto path=$path size=${bytes.size}B uid=$uid")
        val ref = storage.reference.child(path)
        ref.putBytes(bytes).await()
        log.i("putBytes ok, fetching downloadUrl")
        val url = ref.downloadUrl.await().toString()
        log.i("downloadUrl=$url")
        return url
    }
}
