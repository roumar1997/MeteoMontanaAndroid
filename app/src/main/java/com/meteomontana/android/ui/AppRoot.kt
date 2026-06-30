package com.meteomontana.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.auth.AuthManager
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateFcmTokenUseCase
import com.meteomontana.android.ui.components.LanguagePickerDialog
import com.meteomontana.android.ui.components.LanguagePrefs
import com.meteomontana.android.ui.components.applyAppLanguage
import com.meteomontana.android.ui.screens.login.LoginScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@Composable
fun AppRoot(
    deepLink: com.meteomontana.android.DeepLinkTarget? = null,
    onDeepLinkConsumed: () -> Unit = {},
    viewModel: AppRootViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    var showLanguagePicker by remember { mutableStateOf(!LanguagePrefs.hasChosenLanguage(context)) }

    LaunchedEffect(authState) {
        if (authState is AuthManager.AuthState.SignedIn) viewModel.ensureUserProvisioned()
    }

    when (authState) {
        is AuthManager.AuthState.SignedIn -> {
            // Selector de idioma ANTES del onboarding (que está dentro de SchoolListScreen).
            // Se muestra solo la primera vez (hasChosenLanguage=false).
            if (showLanguagePicker) {
                LanguagePickerDialog(
                    onDismiss = { LanguagePrefs.markChosen(context); showLanguagePicker = false },
                    onSelected = { code ->
                        applyAppLanguage(context, code)
                        showLanguagePicker = false
                    }
                )
            } else {
                MainScreen(deepLink = deepLink, onDeepLinkConsumed = onDeepLinkConsumed)
            }
        }
        else -> LoginScreen()
    }
}

@HiltViewModel
class AppRootViewModel @Inject constructor(
    authManager: AuthManager,
    private val getMyProfile: GetMyProfileUseCase,
    private val updateFcmToken: UpdateFcmTokenUseCase
) : ViewModel() {

    val authState: StateFlow<AuthManager.AuthState> = authManager.authState

    fun ensureUserProvisioned() {
        viewModelScope.launch {
            try {
                getMyProfile()  // JIT provisioning
                try {
                    val token = FirebaseMessaging.getInstance().token.await()
                    updateFcmToken(FcmTokenRequest(token))
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }
    }
}
