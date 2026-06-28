package com.meteomontana.android.push

import android.content.Context

/**
 * Silenciado de grupos SOLO en este dispositivo (SharedPreferences).
 * Guarda los convId silenciados; al recibir un push de grupo cuyo convId esté
 * aquí, no se muestra notificación.
 */
object MutedChatsStore {
    private const val PREFS = "muted_chats"
    private const val KEY = "muted_conv_ids"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isMuted(ctx: Context, convId: String): Boolean =
        prefs(ctx).getStringSet(KEY, emptySet())?.contains(convId) == true

    fun setMuted(ctx: Context, convId: String, muted: Boolean) {
        val current = prefs(ctx).getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (muted) current.add(convId) else current.remove(convId)
        prefs(ctx).edit().putStringSet(KEY, current).apply()
    }
}
