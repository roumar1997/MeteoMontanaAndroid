package com.meteomontana.android.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor OkHttp que añade Authorization: Bearer <ID-token> en cada request.
 * Si no hay usuario logueado, no añade nada (el back permite anónimos para
 * endpoints públicos).
 *
 * El token de Firebase caduca cada hora. `getIdToken(false)` lo refresca
 * automáticamente si está caducado — no nos preocupamos por eso.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Bloqueamos brevemente para coger el token (es un await rápido).
        // OkHttp ya corre en hilo de IO, así que es seguro aquí.
        val token: String? = runBlocking { authManager.currentIdToken() }

        val newRequest = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else original

        return chain.proceed(newRequest)
    }
}
