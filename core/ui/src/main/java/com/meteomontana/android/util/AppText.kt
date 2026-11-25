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
        // Se guarda el contexto TAL CUAL: `applicationContext` devuelve siempre la Application original
        // (con el idioma con el que arrancó el proceso) y se ignoraba el idioma recién elegido.
        appContext = context
    }

    fun array(@ArrayRes id: Int): List<String> =
        overrideSource?.array(id) ?: appContext.resources.getStringArray(id).toList()

    fun get(@StringRes id: Int, vararg args: Any?): String {
        overrideSource?.let { return it.string(id, args.toList()) }
        return if (args.isEmpty()) appContext.getString(id)
        else appContext.getString(id, *args.map { it ?: "" }.toTypedArray())
    }

    /** "es" o "en": el idioma en que se pinta la app (los tests JVM van en español). */
    fun lang(): String = if (overrideSource != null) AppLanguage.ES else AppLanguage.effective(appContext)

    /** Nombre de la línea según la modalidad de su piedra: bloque / vía / ascenso (ver [LineWords]). */
    fun lineNoun(discipline: String?, plural: Boolean = false, capitalised: Boolean = false): String =
        com.meteomontana.android.domain.model.LineWords.nounOf(discipline, lang(), plural, capitalised)

    /** "1 bloque", "3 vías", "2 ascensos". */
    fun lineCount(n: Int, discipline: String?): String =
        com.meteomontana.android.domain.model.LineWords.countOf(n, discipline, lang())

    /** Texto con singular / plural ("1 persona", "5 personas"). */
    fun plural(@androidx.annotation.PluralsRes id: Int, count: Int, vararg args: Any?): String =
        appContext.resources.getQuantityString(id, count, *args.map { it ?: "" }.toTypedArray())
}
