package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

@Serializable
data class PhotoUploadUrlDto(val url: String)

/**
 * Sube fotos "Tipo A" (piedra/perfil/nota/quedada) al backend, que las guarda
 * en Cloudflare R2 y devuelve una URL permanente {@code .../api/photo/{key}}.
 * Sustituye a la subida directa a Firebase de las apps (así el egress es gratis
 * en R2). Bearer del interceptor; el backend saca el uid del token.
 */
class KtorPhotoApi(private val client: HttpClient) {

    /**
     * @param category "boulder" | "note" | "meetup" | "profile".
     * @param bytes JPEG ya comprimido por la plataforma.
     */
    suspend fun upload(
        category: String,
        bytes: ByteArray,
        schoolId: String? = null,
        meetupId: String? = null,
        contentType: String = "image/jpeg"
    ): String =
        client.submitFormWithBinaryData(
            url = "photo/upload",
            formData = formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                })
                append("category", category)
                if (schoolId != null) append("schoolId", schoolId)
                if (meetupId != null) append("meetupId", meetupId)
            }
        ).body<PhotoUploadUrlDto>().url
}
