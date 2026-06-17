package com.meteomontana.android.domain.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Puerto de autenticación. Abstrae Firebase Auth del resto de la app.
 * Solo expone lo que el dominio y los ViewModels necesitan saber.
 * Puro Kotlin — sin imports Android/Firebase. Listo para commonMain en Fase 2.
 */
interface AuthService {
    sealed interface AuthState {
        data object Loading : AuthState
        data object SignedOut : AuthState
        data class SignedIn(val uid: String, val email: String?, val displayName: String?) : AuthState
        data class Error(val message: String) : AuthState
    }

    val authState: StateFlow<AuthState>

    /** UID del usuario autenticado o null si no hay sesión. */
    fun currentUid(): String?

    /** ID token de Firebase (para el AuthInterceptor). */
    @Throws(Exception::class)
    suspend fun currentIdToken(forceRefresh: Boolean = false): String?

    /** Cierra sesión. */
    @Throws(Exception::class)
    suspend fun signOut()
}
