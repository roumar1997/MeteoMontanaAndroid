package com.meteomontana.android.ui.theme

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Gestor de tema persistido en SharedPreferences (clave "cumbre.theme").
 * Equivalente al theme.js de la PWA — usamos los mismos identificadores.
 */
@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cumbre.theme", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(load())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    fun toggle() {
        // Toggle directo claro/oscuro (ignora SYSTEM; al pulsar fija explícitamente).
        setMode(if (_mode.value == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    private fun load(): ThemeMode = try {
        val saved = prefs.getString(KEY, null) ?: return ThemeMode.SYSTEM
        ThemeMode.valueOf(saved)
    } catch (_: Throwable) { ThemeMode.SYSTEM }

    companion object { private const val KEY = "mode" }
}
