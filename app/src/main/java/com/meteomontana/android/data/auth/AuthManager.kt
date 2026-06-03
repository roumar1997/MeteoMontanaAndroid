package com.meteomontana.android.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.meteomontana.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encapsula Firebase Auth + Credential Manager (Google Sign-In moderno).
 *
 * Flujo:
 *  1. UI llama signInWithGoogle(activityContext)
 *  2. CredentialManager muestra bottom sheet con cuentas Google
 *  3. Usuario elige cuenta -> obtenemos GoogleIdToken
 *  4. Lo intercambiamos por credencial de Firebase
 *  5. authState pasa a Authenticated(user)
 *
 * Cualquier Composable observa `authState` con collectAsState() para reaccionar.
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    sealed interface AuthState {
        data object Loading : AuthState
        data object SignedOut : AuthState
        data class SignedIn(val user: FirebaseUser) : AuthState
        data class Error(val message: String) : AuthState
    }

    private val _authState = MutableStateFlow<AuthState>(
        firebaseAuth.currentUser
            ?.let { AuthState.SignedIn(it) }
            ?: AuthState.SignedOut
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val credentialManager: CredentialManager = CredentialManager.create(context)

    /**
     * Lanza el flujo de Google Sign-In.
     * `activityContext` debe ser el Activity actual (no el Application context).
     */
    suspend fun signInWithGoogle(activityContext: Context) {
        _authState.value = AuthState.Loading
        try {
            // Web client ID (client_type=3) — Firebase lo añade auto a strings.xml
            // como `default_web_client_id` cuando aplicas el plugin google-services.
            val webClientId = context.getString(R.string.default_web_client_id)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val credential = result.credential
            if (credential is CustomCredential
                && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(
                    googleIdTokenCredential.idToken, null
                )
                val authResult = firebaseAuth.signInWithCredential(firebaseCred).await()
                val user = authResult.user
                _authState.value = if (user != null)
                    AuthState.SignedIn(user)
                else
                    AuthState.Error("No Firebase user")
            } else {
                _authState.value = AuthState.Error("Tipo de credencial no soportado")
            }
        } catch (e: GetCredentialException) {
            _authState.value = AuthState.Error(e.message ?: "Sign-in cancelado")
        } catch (e: GoogleIdTokenParsingException) {
            _authState.value = AuthState.Error("Token inválido")
        } catch (e: Throwable) {
            _authState.value = AuthState.Error(e.message ?: "Error desconocido")
        }
    }

    suspend fun signOut() {
        firebaseAuth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Throwable) {}
        _authState.value = AuthState.SignedOut
    }

    /** Devuelve el ID token actual o null. Lo usa el AuthInterceptor. */
    suspend fun currentIdToken(forceRefresh: Boolean = false): String? =
        firebaseAuth.currentUser?.getIdToken(forceRefresh)?.await()?.token
}
