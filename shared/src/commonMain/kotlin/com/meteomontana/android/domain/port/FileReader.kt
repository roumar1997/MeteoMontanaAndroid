package com.meteomontana.android.domain.port

import com.meteomontana.android.domain.model.FileRef

/**
 * Puerto para leer bytes de un fichero local.
 * La implementación Android usa ContentResolver. En iOS usará NSData.
 * Puro Kotlin — sin imports Android. Listo para commonMain en Fase 2.
 */
interface FileReader {
    @Throws(Exception::class)
    suspend fun readBytes(ref: FileRef): ByteArray

    /**
     * Lee y comprime una imagen del FileRef:
     * - Redimensiona conservando aspecto para que el lado mayor sea como mucho [maxDim] px.
     * - Recomprime a JPEG con calidad [quality] (0-100).
     * - Devuelve los bytes JPEG resultantes.
     *
     * Implementaciones por plataforma:
     * - Android: BitmapFactory + Bitmap.createScaledBitmap + compress(JPEG).
     * - iOS: UIImage + jpegData(compressionQuality:).
     */
    @Throws(Exception::class)
    suspend fun readImageCompressed(ref: FileRef, maxDim: Int = 1024, quality: Int = 80): ByteArray
}
