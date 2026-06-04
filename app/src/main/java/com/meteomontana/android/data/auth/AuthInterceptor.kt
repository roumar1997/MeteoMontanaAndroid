package com.meteomontana.android.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor OkHttp que añade Authorization: Bearer <ID-token> en cada request.
 * OkHttp ejecuta esto en un thread de IO, así que runBlocking aquí no bloquea UI.
 *
 * Nota: NO reintentamos en 401/403 automáticamente porque eso multiplicaría
 * el tiempo de cada petición cuando hay un problema sostenido (como Open-Meteo
 * caído) y provocaría ANRs por agotamiento del thread pool.
 *
 * Si el token caduca, el usuario verá el error y puede refrescar manualmente
 * o hacer logout/login.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token: String? = runBlocking { authManager.currentIdToken(forceRefresh = false) }

        val req = if (token != null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else original

        return chain.proceed(req)
    }
}
