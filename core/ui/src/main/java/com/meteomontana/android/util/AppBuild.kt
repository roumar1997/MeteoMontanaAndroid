package com.meteomontana.android.util

/**
 * Datos del build de la app que necesitan los módulos (que no ven el `BuildConfig` de `:app`).
 * Los rellena `MeteoMontanaApp.onCreate` antes de pintar nada.
 */
object AppBuild {
    /** Base del API, con la barra final y `api/` (p. ej. `https://api.climbingteams.com/api/`). */
    var apiBaseUrl: String = "https://api.climbingteams.com/api/"
        private set

    var isDebug: Boolean = false
        private set

    fun init(apiBaseUrl: String, isDebug: Boolean) {
        this.apiBaseUrl = apiBaseUrl
        this.isDebug = isDebug
    }

    /** Raíz pública de la web del API (sin `api/`), para enlaces compartidos. */
    val webBaseUrl: String get() = apiBaseUrl.removeSuffix("api/")
}
