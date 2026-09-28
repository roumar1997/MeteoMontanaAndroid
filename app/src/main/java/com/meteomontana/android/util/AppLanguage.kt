package com.meteomontana.android.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Idioma elegido DENTRO de la app (Perfil → Ajustes → Idioma).
 *
 * Tres valores: [SYSTEM] (sigue el idioma del móvil), [ES] o [EN]. Se guarda en
 * SharedPreferences y se aplica envolviendo el `Context` de la Application y de
 * cada Activity ([wrap]); así `stringResource`, `getString` y [AppText] leen el
 * mismo idioma sin depender de `AppCompatDelegate` (que en Android 12 y
 * anteriores exige AppCompatActivity).
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val ES = "es"
    const val EN = "en"

    private const val PREFS = "app_language"
    private const val KEY = "lang"

    /** Idioma del sistema, capturado UNA vez: `Locale.setDefault` lo pisa después. */
    private val systemLocale: Locale by lazy { Resources.getSystem().configuration.locales[0] }

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }

    /** "es" o "en": el idioma con el que se está pintando la app de verdad. */
    fun effective(context: Context): String = when (get(context)) {
        ES -> ES
        EN -> EN
        else -> if (systemLocale.language == "en") EN else ES   // la app solo tiene es/en; el resto cae a es
    }

    /** Devuelve `base` con el idioma elegido aplicado (o sin cambios si sigue al sistema). */
    fun wrap(base: Context): Context {
        val chosen = get(base)
        val locale = when (chosen) {
            ES -> Locale("es")
            EN -> Locale("en")
            else -> systemLocale
        }
        Locale.setDefault(locale)
        if (chosen == SYSTEM) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
