package com.meteomontana.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.meteomontana.android.R
import com.meteomontana.android.data.api.KtorAppVersionApi
import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.auth.AuthManager
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateFcmTokenUseCase
import com.meteomontana.android.ui.screens.login.LoginScreen
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    // Actualización OBLIGATORIA: si el backend dice que esta versión es
    // demasiado vieja, gate no descartable con botón a la tienda. Tolerante a
    // fallos: sin red o error → null → la app abre normal.
    val forceUpdateUrl by viewModel.forceUpdateUrl.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthManager.AuthState.SignedIn) viewModel.ensureUserProvisioned()
    }

    forceUpdateUrl?.let { url ->
        ForceUpdateScreen(url)
        return
    }

    when (authState) {
        is AuthManager.AuthState.SignedIn -> MainScreen(deepLink = deepLink, onDeepLinkConsumed = onDeepLinkConsumed)
        else -> LoginScreen()
    }
}

/** Pantalla completa NO descartable: hay que actualizar para seguir usando la app.
 *  Primero intenta el flujo de Play In-App Updates (descarga+instala DENTRO de
 *  la app, sin ir a la tienda). Si no está disponible (instalación fuera de
 *  Play, Play sin la versión aún, cualquier error) queda esta pantalla con el
 *  botón a la tienda como respaldo. */
@Composable
private fun ForceUpdateScreen(storeUrl: String) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        runCatching {
            val activity = generateSequence(context) {
                (it as? android.content.ContextWrapper)?.baseContext
            }.filterIsInstance<android.app.Activity>().firstOrNull() ?: return@runCatching
            val manager = com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(context)
            manager.appUpdateInfo.addOnSuccessListener { info ->
                val available = info.updateAvailability() ==
                    com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
                if (available && info.isUpdateTypeAllowed(
                        com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE)) {
                    runCatching {
                        manager.startUpdateFlowForResult(
                            info, activity,
                            com.google.android.play.core.appupdate.AppUpdateOptions
                                .newBuilder(com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE)
                                .build(),
                            9001
                        )
                    }
                }
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.force_update_eyebrow),
            style = EyebrowTextStyle,
            color = Terra
        )
        Text(
            stringResource(R.string.force_update_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            stringResource(R.string.force_update_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Terra)
                .clickable {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(storeUrl)
                            )
                        )
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.force_update_button),
                style = EyebrowTextStyle,
                color = Color.White
            )
        }
    }
}

@HiltViewModel
class AppRootViewModel @Inject constructor(
    authManager: AuthManager,
    private val getMyProfile: GetMyProfileUseCase,
    private val updateFcmToken: UpdateFcmTokenUseCase,
    private val appVersionApi: KtorAppVersionApi
) : ViewModel() {

    val authState: StateFlow<AuthManager.AuthState> = authManager.authState

    /** URL de la tienda si esta versión está por debajo del mínimo obligatorio
     *  del backend; null = todo bien (o no se pudo comprobar — nunca bloquea). */
    private val _forceUpdateUrl = MutableStateFlow<String?>(null)
    val forceUpdateUrl: StateFlow<String?> = _forceUpdateUrl

    init {
        viewModelScope.launch {
            runCatching {
                val v = appVersionApi.get()
                if (com.meteomontana.android.BuildConfig.VERSION_CODE < v.minAndroidVc) {
                    _forceUpdateUrl.value = v.androidUrl
                        ?: "https://play.google.com/store/apps/details?id=com.meteomontana.android"
                }
            }
        }
    }

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
