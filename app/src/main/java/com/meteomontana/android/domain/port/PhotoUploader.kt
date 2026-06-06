package com.meteomontana.android.domain.port

/**
 * Puerto para subir fotos a almacenamiento remoto.
 * La implementación Android usa Firebase Storage. En iOS se podría reusar el mismo SDK.
 * Puro Kotlin — sin imports Android/Firebase. Listo para commonMain en Fase 2.
 */
interface PhotoUploader {
    /**
     * Sube los bytes de una foto de boulder y devuelve la URL de descarga.
     * @param bytes Contenido de la imagen.
     * @param mimeType MIME type, p.ej. "image/jpeg".
     * @param schoolId ID de la escuela (se usa en el path de Storage).
     */
    suspend fun uploadBoulderPhoto(bytes: ByteArray, mimeType: String, schoolId: String): String
}
