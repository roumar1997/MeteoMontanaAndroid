package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.GymPhotoUrlDto
import com.meteomontana.android.data.api.dto.GymReportDto
import com.meteomontana.android.data.api.dto.GymReportRequestDto
import com.meteomontana.android.data.api.dto.NewGymRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

/** Correcciones de ficha, altas de muros nuevos y la foto que las acompaña. Todo queda pendiente de un admin. */
class KtorGymReportApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun submitCorrection(gymId: Long, body: GymReportRequestDto): GymReportDto =
        client.post("gyms/$gymId/reports") { setBody(body) }.body()

    @Throws(Exception::class)
    suspend fun submitNewGym(body: NewGymRequestDto): GymReportDto =
        client.post("gyms/reports") { setBody(body) }.body()

    /** Sube la foto del reporte (JPEG ya comprimido, max 5 MB) y devuelve su URL. */
    @Throws(Exception::class)
    suspend fun uploadPhoto(bytes: ByteArray, contentType: String = "image/jpeg"): String =
        client.submitFormWithBinaryData(
            url = "gyms/reports/photo",
            formData = formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                })
            }
        ).body<GymPhotoUrlDto>().url
}
