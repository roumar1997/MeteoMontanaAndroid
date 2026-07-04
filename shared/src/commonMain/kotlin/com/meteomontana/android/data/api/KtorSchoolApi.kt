package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.SchoolDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/** Catálogo + ETag de la respuesta. schools == null ⇒ 304, reusar caché local. */
data class SchoolsCatalogResponse(val schools: List<SchoolDto>?, val etag: String?)

class KtorSchoolApi(private val client: HttpClient) {

    /**
     * Catálogo completo condicional: manda If-None-Match si tenemos un ETag
     * previo. expectSuccess se desactiva en esta request porque el 304 es un
     * status 3xx y el cliente lo trataría como error de redirección.
     */
    suspend fun getSchoolsCatalog(etag: String?): SchoolsCatalogResponse {
        val resp = client.get("schools") {
            expectSuccess = false
            etag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
        return when {
            resp.status == HttpStatusCode.NotModified ->
                SchoolsCatalogResponse(schools = null, etag = etag)
            resp.status.isSuccess() ->
                SchoolsCatalogResponse(schools = resp.body(), etag = resp.headers[HttpHeaders.ETag])
            else -> throw ResponseException(resp, resp.bodyAsText())
        }
    }

    suspend fun getSchools(
        region: String? = null,
        style: String? = null,
        rockType: List<String>? = null,
        lat: Double? = null,
        lon: Double? = null,
        radioKm: Double? = null
    ): List<SchoolDto> = client.get("schools") {
        region?.let { parameter("region", it) }
        style?.let { parameter("style", it) }
        rockType?.forEach { parameter("rockType", it) }
        lat?.let { parameter("lat", it) }
        lon?.let { parameter("lon", it) }
        radioKm?.let { parameter("radioKm", it) }
    }.body()

    suspend fun searchSchools(query: String, limit: Int = 10): List<SchoolDto> =
        client.get("schools/search") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    /** Búsqueda GLOBAL de vías/bloques por nombre en todo el catálogo. */
    suspend fun searchLines(query: String): List<LineSearchHitDto> =
        client.get("search/lines") { parameter("q", query) }.body()

    suspend fun getSchoolById(id: String): SchoolDto = client.get("schools/$id").body()

    suspend fun getMonthlyStats(id: String): MonthlyStatsDto =
        client.get("schools/$id/monthly-stats").body()
}

@kotlinx.serialization.Serializable
data class MonthlyStatsDto(
    val scores: List<Int> = emptyList(),
    val bestRange: String? = null
)

/** Resultado del buscador GLOBAL de vías/bloques (pantalla de Escuelas). */
@kotlinx.serialization.Serializable
data class LineSearchHitDto(
    val schoolId: String,
    val schoolName: String = "",
    val blockId: String,
    val blockName: String = "",
    val lineId: String? = null,
    val lineName: String? = null,
    val grade: String? = null
)
