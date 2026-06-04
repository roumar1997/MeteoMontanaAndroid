package com.meteomontana.android.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor OkHttp que añade Authorization: Bearer <ID-token> en cada request.
 *
 * Si el primer intento recibe 401/403, refresca el token de Firebase forzosamente
 * y reintenta la petición una vez. Así si el token ha caducado, se recupera
 * automáticamente sin que el usuario tenga que hacer logout.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Intento 1: token cacheado (rápido)
        val token: String? = runBlocking { authManager.currentIdToken(forceRefresh = false) }

        val response = chain.proceed(
            if (token != null) original.newBuilder()
                .header("Authorization", "Bearer $token").build()
            else original
        )

        // Si recibimos 401 o 403 Y tenemos usuario, refrescamos el token y reintentamos
        if ((response.code == 401 || response.code == 403) && token != null) {
            response.close()
            val freshToken: String? = runBlocking { authManager.currentIdToken(forceRefresh = true) }
            if (freshToken != null && freshToken != token) {
                return chain.proceed(
                    original.newBuilder()
                        .header("Authorization", "Bearer $freshToken").build()
                )
            }
        }

        return response
    }
}
