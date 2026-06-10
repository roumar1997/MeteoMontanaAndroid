package com.meteomontana.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
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
        val type = intent?.getStringExtra("targetType") ?: return
        val id = intent.getStringExtra("targetId")
        pendingDeepLink.value = DeepLinkTarget(type, id)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore result */ }
            launcher.launch("android.permission.POST_NOTIFICATIONS")
        }
    }
}

data class DeepLinkTarget(val targetType: String, val targetId: String?)
