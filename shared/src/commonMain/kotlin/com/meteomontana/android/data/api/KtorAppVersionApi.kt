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
    /** El caller debe tolerar el fallo (sin red → la app abre normal). */
    suspend fun get(): AppVersionDto = client.get("app-version").body()
}
