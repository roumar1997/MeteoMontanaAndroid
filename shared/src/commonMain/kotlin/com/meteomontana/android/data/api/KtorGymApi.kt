package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.GymDto
import com.meteomontana.android.data.api.dto.GymNearDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/** Catalogo + ETag. gyms == null => 304, reusar la copia local. */
data class GymsCatalogResponse(val gyms: List<GymDto>?, val etag: String?)

/** Catalogo de rocodromos (publico), ficha, "cerca de mi" y favoritos (con sesion). */
class KtorGymApi(private val client: HttpClient) {

    /** Lista entera condicional (If-None-Match). El 304 se desactiva como error solo en esta peticion. */
    @Throws(Exception::class)
    suspend fun getCatalog(etag: String?): GymsCatalogResponse {
        val resp = client.get("gyms") {
            expectSuccess = false
            etag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
        return when {
            resp.status == HttpStatusCode.NotModified -> GymsCatalogResponse(null, etag)
            resp.status.isSuccess() -> GymsCatalogResponse(resp.body(), resp.headers[HttpHeaders.ETag])
            else -> throw ResponseException(resp, resp.bodyAsText())
        }
    }

    @Throws(Exception::class)
    suspend fun getGym(id: Long): GymDto = client.get("gyms/$id").body()

    @Throws(Exception::class)
    suspend fun near(lat: Double, lon: Double, radiusKm: Double, limit: Int): List<GymNearDto> =
        client.get("gyms/near") {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("radiusKm", radiusKm)
            parameter("limit", limit)
        }.body()

    /** Ids de los rocodromos favoritos del usuario. */
    @Throws(Exception::class)
    suspend fun getFavoriteIds(): List<Long> = client.get("gyms/favorites").body()

    @Throws(Exception::class)
    suspend fun addFavorite(id: Long) { client.put("gyms/$id/favorite") }

    @Throws(Exception::class)
    suspend fun removeFavorite(id: Long) { client.delete("gyms/$id/favorite") }
}
