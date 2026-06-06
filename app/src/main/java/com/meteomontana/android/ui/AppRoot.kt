package com.meteomontana.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.auth.AuthManager
import com.meteomontana.android.ui.screens.login.LoginScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Pantalla raíz que decide entre Login y App principal según el estado de auth.
 * En login exitoso, llama una vez a GET /api/me para provisionar el usuario en
 * la BD del back (JIT provisioning de Fase 8).
 */
@Composable
fun AppRoot(viewModel: AppRootViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsState()

    // JIT provisioning al primer login.
    LaunchedEffect(authState) {
        if (authState is AuthManager.AuthState.SignedIn) viewModel.ensureUserProvisioned()
    }

    when (authState) {
        is AuthManager.AuthState.SignedIn -> MainScreen()
        else -> LoginScreen()
    }
}

@HiltViewModel
class AppRootViewModel @Inject constructor(
    authManager: AuthManager,
    private val api: KtorProfileApi
) : ViewModel() {

    val authState: StateFlow<AuthManager.AuthState> = authManager.authState

    fun ensureUserProvisioned() {
        viewModelScope.launch {
            try {
                api.getMyProfile()  // JIT provisioning
                // Registrar token FCM para push
                try {
                    val token = FirebaseMessaging.getInstance().token.await()
                    api.updateFcmToken(FcmTokenRequest(token))
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }
    }
}
