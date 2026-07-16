package com.meteomontana.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.meteomontana.android.ui.AppRoot
import com.meteomontana.android.ui.theme.MeteoMontanaTheme
import com.meteomontana.android.ui.theme.ThemeManager
import com.meteomontana.android.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeManager: ThemeManager

    // Deep link en caliente: el push abrió la app con extras → guardamos para que AppRoot
    // los lea y navegue.
    private val pendingDeepLink = mutableStateOf<DeepLinkTarget?>(null)

    // El launcher de permisos DEBE registrarse como campo (antes de STARTED). Si se
    // registra dentro de onCreate y se lanza en línea, la Activity Result API puede
    // tirar IllegalStateException o no mostrar el diálogo → el permiso de
    // notificaciones nunca se concede y NO llega ninguna push (Android 13+).
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Encadenado de permisos: Android solo muestra UN diálogo a la vez;
            // hasta que este no se responde, el de ubicación se descartaba en
            // silencio (había que cerrar y reabrir la app). La puerta avisa a
            // las pantallas de que ya pueden pedir el suyo.
            PermissionsGate.open.value = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen API: pinta fondo papel + montaña al instante en vez de
        // pantalla en blanco mientras la app inicializa. Debe ir antes de super.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntentExtras(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            val mode by themeManager.mode.collectAsState()
            val system = isSystemInDarkTheme()
            val isDark = when (mode) {
                ThemeMode.DARK   -> true
                ThemeMode.LIGHT  -> false
                ThemeMode.SYSTEM -> system
            }
            MeteoMontanaTheme(darkTheme = isDark) {
                AppRoot(deepLink = pendingDeepLink.value, onDeepLinkConsumed = { pendingDeepLink.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIntentExtras(intent)
    }

    private fun consumeIntentExtras(intent: Intent?) {
        // App Link compartido: https://.../s/e/{escuela} o /s/v/{escuela}/{via}.
        intent?.data?.let { uri ->
            val seg = uri.pathSegments
            if (seg.firstOrNull() == "s") {
                when (seg.getOrNull(1)) {
                    "q" -> seg.getOrNull(2)?.let { meetupId ->
                        com.meteomontana.android.domain.usecase.meetups.PendingMeetupInvite
                            .set(meetupId, uri.getQueryParameter("i"))
                        pendingDeepLink.value = DeepLinkTarget("meetup", meetupId)
                    }
                    "e" -> seg.getOrNull(2)?.let { pendingDeepLink.value = DeepLinkTarget("school", it) }
                    "v" -> {
                        val school = seg.getOrNull(2); val line = seg.getOrNull(3)
                        if (school != null && line != null)
                            pendingDeepLink.value = DeepLinkTarget("via", "$school|$line")
                    }
                    // Perfil compartido: /s/u/{username o uid}.
                    "u" -> seg.getOrNull(2)?.let { pendingDeepLink.value = DeepLinkTarget("user", it) }
                    // Publicación del feed compartida: /s/p/{postId}.
                    "p" -> seg.getOrNull(2)?.let { pendingDeepLink.value = DeepLinkTarget("feed_post", it) }
                }
                intent.data = null   // no re-navegar en recreaciones
                return
            }
        }
        val type = intent?.getStringExtra("targetType") ?: return
        val id = intent.getStringExtra("targetId")
        pendingDeepLink.value = DeepLinkTarget(type, id)
        // Borramos los extras YA usados: al girar el móvil el sistema recrea la
        // Activity y onCreate vuelve a leer este mismo intent → re-navegaría al
        // deep-link viejo (p.ej. abría "Solicitudes" en cada rotación). Sin extras
        // ya no se re-dispara.
        intent.removeExtra("targetType")
        intent.removeExtra("targetId")
        setIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, "android.permission.POST_NOTIFICATIONS"
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            } else {
                PermissionsGate.open.value = true
            }
        } else {
            PermissionsGate.open.value = true
        }
    }
}

/**
 * Puerta del encadenado de permisos: se abre cuando el diálogo de
 * notificaciones ya está respondido (o no aplica) → entonces las pantallas
 * pueden pedir ubicación sin que Android lo descarte.
 */
object PermissionsGate {
    val open = kotlinx.coroutines.flow.MutableStateFlow(false)
}

data class DeepLinkTarget(val targetType: String, val targetId: String?)
