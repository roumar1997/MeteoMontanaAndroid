package com.meteomontana.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Hace que los iconos de la barra de estado/navegación de un Dialog a pantalla
 * completa sigan el TEMA DE LA APP (fondo del tema), no el modo del sistema.
 * Cada Dialog tiene su propia ventana: la configuración de MainActivity no le
 * llega. Llamar al principio del contenido del Dialog.
 */
@Composable
fun DialogSystemBars() {
    val view = LocalView.current
    val oscuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !oscuro
        c.isAppearanceLightNavigationBars = !oscuro
    }
}
