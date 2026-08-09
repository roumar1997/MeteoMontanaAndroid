package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

/**
 * Catálogo de países y sus regiones (GET /api/geo/countries, público).
 *
 * Lo sirve el backend a propósito: abrir un país nuevo es entonces un
 * despliegue del servidor y no una versión nueva en Play y en la App Store,
 * con los días de revisión que eso arrastra.
 *
 * Antes de esto, las regiones se deducían de las escuelas que ya existían, así
 * que un país recién abierto salía con el desplegable vacío y el primero que
 * quisiera proponer una escuela allí no podía elegir región.
 */
@Serializable
data class CountryDto(
    val code: String,
    val name: String,
    val regions: List<String> = emptyList()
)

class KtorGeoApi(private val client: HttpClient) {
    /**
     * El caller debe tolerar el fallo: sin red se cae a España, que es lo que
     * había antes del catálogo y cubre el 100% de las escuelas de hoy.
     *
     * `@Throws(Exception::class)` NO es opcional: esta suspend se llama desde
     * Swift, y sin la anotación una excepción de red aborta el proceso.
     */
    @Throws(Exception::class)
    suspend fun countries(): List<CountryDto> = client.get("geo/countries").body()
}
