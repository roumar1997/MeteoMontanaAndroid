package com.meteomontana.android.util

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes

/**
 * Acceso a los textos traducibles desde código que NO es @Composable
 * (callbacks de clic, ViewModels, funciones auxiliares).
 *
 * En una función @Composable se sigue usando `stringResource`; esto existe
 * para no arrastrar un `Context` por cada lambda que solo quiere un mensaje.
 * Se inicializa una vez en [com.meteomontana.android.MeteoMontanaApp].
 */
object AppText {
    /** Origen alternativo de textos (tests JVM, donde no hay recursos de Android). */
    interface Source {
        fun string(@StringRes id: Int, args: List<Any?>): String
        fun array(@ArrayRes id: Int): List<String>
    }

    private lateinit var appContext: Context

    /** Solo lo usan los tests unitarios; en la app es siempre `null`. */
    @androidx.annotation.VisibleForTesting
    var overrideSource: Source? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun array(@ArrayRes id: Int): List<String> =
        overrideSource?.array(id) ?: appContext.resources.getStringArray(id).toList()

    fun get(@StringRes id: Int, vararg args: Any?): String {
        overrideSource?.let { return it.string(id, args.toList()) }
        return if (args.isEmpty()) appContext.getString(id)
        else appContext.getString(id, *args.map { it ?: "" }.toTypedArray())
    }
}
