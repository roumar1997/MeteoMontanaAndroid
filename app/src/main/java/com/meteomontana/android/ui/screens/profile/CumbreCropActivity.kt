package com.meteomontana.android.ui.screens.profile

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.yalantis.ucrop.UCropActivity

/**
 * Subclase de UCropActivity SOLO para poder desactivar el borde-a-borde
 * automático que Android fuerza desde targetSdk 35+.
 *
 * El primer arreglo (2026-08-16, Theme.MeteoMontana.Crop con
 * `android:fitsSystemWindows`) no fue suficiente: ese atributo es el
 * mecanismo ANTIGUO de márgenes, y con targetSdk 35+ el sistema llama él
 * mismo a `Window.setDecorFitsSystemWindows(false)` sin mirar el tema — la
 * declaración XML queda ignorada. El ✓ de aceptar seguía debajo del reloj
 * en un Xiaomi real (Álvaro, 2026-08-24/25, tras haberlo dado por
 * solucionado).
 *
 * La forma correcta desde la API de insets es código, no tema: pedirlo
 * ANTES de `super.onCreate()`, que es cuando uCrop mide su Toolbar. No se
 * puede tocar `UCropActivity.onCreate()` (es de una librería externa), así
 * que se sobreescribe en esta subclase — ver AndroidManifest.xml, que
 * declara ESTA clase en vez de la de la librería.
 */
class CumbreCropActivity : UCropActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
    }
}
