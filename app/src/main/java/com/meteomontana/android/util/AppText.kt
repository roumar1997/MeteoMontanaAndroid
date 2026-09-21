package com.meteomontana.android.util

import android.content.Context
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
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any?): String =
        if (args.isEmpty()) appContext.getString(id)
        else appContext.getString(id, *args.map { it ?: "" }.toTypedArray())
}
