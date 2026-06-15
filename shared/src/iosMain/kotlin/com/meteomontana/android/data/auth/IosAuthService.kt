package com.meteomontana.android.data.auth

import com.meteomontana.android.domain.port.AuthService
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Bridge de autenticación que IMPLEMENTA Swift (con FirebaseAuth). Solo
 * lecturas síncronas + callbacks: Firebase iOS es un SDK ObjC/Swift que no
 * existe en Kotlin/Native, así que toda la fontanería vive en Swift y aquí
 * solo la envolvemos.
 *
 * Equivalente iOS del `FirebaseAuthService` de androidMain.
 */
interface IosAuthBridge {
    fun currentUid(): String?
    fun currentEmail(): String?
    fun currentDisplayName(): String?

    /** ID token de Firebase (lo usa el tokenProvider del HttpClient). */
    fun currentIdToken(forceRefresh: Boolean, callback: (String?) -> Unit)

    fun signOut(callback: () -> Unit)

    /**
     * Registra un observador del estado de sesión (Firebase
     * addStateDidChangeListener). El callback se invoca en cada cambio; el lado
     * Kotlin re-lee `currentUid/Email/DisplayName` para recomponer el estado.
     */
    fun observeAuthState(callback: () -> Unit)
}

/**
 * Implementación iOS de [AuthService]. Mantiene el [StateFlow] del estado de
 * sesión (lo consumen los ViewModels/SwiftUI) y traduce los callbacks del
 * bridge Swift a `suspend`, igual que [com.meteomontana.android.data.location.IosLocationProvider].
 */
class IosAuthService(
    private val bridge: IosAuthBridge,
) : AuthService {

    private val _authState = MutableStateFlow(computeState())
    override val authState: StateFlow<AuthService.AuthState> = _authState.asStateFlow()

    init {
        bridge.observeAuthState { _authState.value = computeState() }
    }

    override fun currentUid(): String? = bridge.currentUid()

    override suspend fun currentIdToken(forceRefresh: Boolean): String? =
        suspendCancellableCoroutine { cont ->
            bridge.currentIdToken(forceRefresh) { token ->
                if (cont.isActive) cont.resume(token)
            }
        }

    override suspend fun signOut() {
        suspendCancellableCoroutine<Unit> { cont ->
            bridge.signOut {
                _authState.value = AuthService.AuthState.SignedOut
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun computeState(): AuthService.AuthState {
        val uid = bridge.currentUid() ?: return AuthService.AuthState.SignedOut
        return AuthService.AuthState.SignedIn(
            uid = uid,
            email = bridge.currentEmail(),
            displayName = bridge.currentDisplayName(),
        )
    }
}
