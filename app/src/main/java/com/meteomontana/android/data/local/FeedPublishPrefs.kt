package com.meteomontana.android.data.local

import android.content.Context

/**
 * Preferencia "Publicar ascensos en el feed" (SharedPreferences, mismo fichero
 * "feed" donde ya se persiste el chip de la pestaña Comunidad):
 * - ASK (default): al marcar HECHO se abre la hoja de publicar.
 * - ALWAYS: publica directo sin preguntar.
 * - NEVER: ni pregunta ni publica (solo diario).
 */
enum class FeedPublishMode { ASK, ALWAYS, NEVER }

object FeedPublishPrefs {
    private const val FILE = "feed"
    private const val KEY = "publish_mode"

    fun get(context: Context): FeedPublishMode {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return FeedPublishMode.ASK
        return runCatching { FeedPublishMode.valueOf(raw) }.getOrDefault(FeedPublishMode.ASK)
    }

    fun set(context: Context, mode: FeedPublishMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }
}

/**
 * Interruptor "Compartir en Comunidad" de la hoja de ascenso: arranca como lo dejaste la última vez (apagado la
 * primera). Quien tenía el ajuste antiguo en "Siempre" lo conserva encendido. Reemplaza al Preguntar/Siempre/Nunca.
 */
object FeedSharePref {
    private const val FILE = "feed"
    private const val KEY = "share_default"

    fun enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY)) prefs.getBoolean(KEY, false)
        else FeedPublishPrefs.get(context) == FeedPublishMode.ALWAYS
    }

    fun set(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }
}
