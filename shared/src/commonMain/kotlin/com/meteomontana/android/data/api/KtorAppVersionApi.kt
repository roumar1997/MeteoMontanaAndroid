package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

/**
 * Versión mínima OBLIGATORIA de las apps (GET /api/app-version, público).
 * Si la app instalada va por debajo del mínimo de su plataforma, muestra un
 * gate no descartable con el botón a su tienda. Los mínimos los controla el
 * backend (env de Railway) — subir el número fuerza la actualización al
 * instante. 0 = no se obliga a nadie.
 */
@Serializable
data class AppVersionDto(
    val minAndroidVc: Int = 0,
    val minIosBuild: Int = 0,
    val androidUrl: String? = null,
    val iosUrl: String? = null
)

class KtorAppVersionApi(private val client: HttpClient) {
    /**
     * El caller debe tolerar el fallo (sin red → la app abre normal).
     *
     * `@Throws(Exception::class)` NO es opcional: esta suspend se llama desde
     * Swift (`container.appVersionApi.get()`) con `try?`. Sin la anotación,
     * SKIE genera una firma Swift NO-throwing → cuando Ktor lanza sin red
     * (`DarwinHttpRequestException`), Swift no puede recibir el error, la
     * excepción escapa a Kotlin/Native y ABORTA el proceso (crash al arrancar
     * offline). Regla: toda suspend expuesta a Swift que haga I/O va con @Throws.
     */
    @Throws(Exception::class)
    suspend fun get(): AppVersionDto = client.get("app-version").body()
}
