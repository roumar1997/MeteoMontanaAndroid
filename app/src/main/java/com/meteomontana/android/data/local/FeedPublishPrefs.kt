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
