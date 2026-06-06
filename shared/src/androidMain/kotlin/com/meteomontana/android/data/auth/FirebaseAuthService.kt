package com.meteomontana.android.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.meteomontana.android.domain.port.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthService(
    private val firebaseAuth: FirebaseAuth
) : AuthService {

    private val _authState = MutableStateFlow(computeState())
    override val authState: StateFlow<AuthService.AuthState> = _authState.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { fa ->
            _authState.value = computeState(fa)
        }
    }

    override fun currentUid(): String? = firebaseAuth.currentUser?.uid

    override suspend fun currentIdToken(forceRefresh: Boolean): String? =
        firebaseAuth.currentUser?.getIdToken(forceRefresh)?.await()?.token

    override suspend fun signOut() {
        firebaseAuth.signOut()
        _authState.value = AuthService.AuthState.SignedOut
    }

    private fun computeState(fa: FirebaseAuth = firebaseAuth): AuthService.AuthState {
        val user = fa.currentUser ?: return AuthService.AuthState.SignedOut
        return AuthService.AuthState.SignedIn(
            uid = user.uid,
            email = user.email,
            displayName = user.displayName
        )
    }
}
