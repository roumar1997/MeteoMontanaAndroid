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
    @Throws(Exception::class)
    suspend fun uploadBoulderPhoto(bytes: ByteArray, mimeType: String, schoolId: String): String

    /** Sube la foto de perfil del usuario actual y devuelve la URL pública. */
    @Throws(Exception::class)
    suspend fun uploadProfilePhoto(bytes: ByteArray, mimeType: String): String

    /** Sube la foto adjunta a una nota comunitaria y devuelve la URL pública. */
    @Throws(Exception::class)
    suspend fun uploadNotePhoto(bytes: ByteArray, mimeType: String, schoolId: String): String

    /** Sube la foto de una quedada y devuelve la URL pública. */
    @Throws(Exception::class)
    suspend fun uploadMeetupPhoto(bytes: ByteArray, mimeType: String, meetupId: String): String
}
