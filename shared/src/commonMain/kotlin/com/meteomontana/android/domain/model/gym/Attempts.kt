package com.meteomontana.android.domain.model.gym

/**
 * Intentos que costo una ascension (opcional: nada seleccionado por defecto). 1 intento = FLASH. Igual en iOS y Android; el
 * servidor guarda 1 a 99, el selector ofrece 1, 2, 3, 4 y «5+» (de 5 a [MAX_PICK]).
 */
object Attempts {
    const val FLASH = 1
    const val MAX_PICK = 30
    const val MAX_STORED = 99

    /** Los chips grandes del selector; el siguiente es «5+», que abre la lista de 5 a [MAX_PICK]. */
    val QUICK: List<Int> = listOf(1, 2, 3, 4)
    val MORE: List<Int> = (5..MAX_PICK).toList()

    /** Un valor valido (1 a 99) o null. */
    fun clean(n: Int?): Int? = n?.takeIf { it in 1..MAX_STORED }

    fun isFlash(n: Int?): Boolean = n == FLASH

    /** ¿El chip «5+» esta activo? (cualquier valor de 5 en adelante). */
    fun isFivePlus(n: Int?): Boolean = n != null && n > QUICK.last()

    /** El mejor (menor) de varios, ignorando los que faltan; null si no hay ninguno. */
    fun best(values: Iterable<Int?>): Int? = values.mapNotNull { clean(it) }.minOrNull()

    fun best(a: Int?, b: Int?): Int? = best(listOf(a, b))

    /** «1 intento» / «3 intentos» lo pinta cada app con su plural; aqui solo si merece un aviso (mas de uno). */
    fun showsCount(n: Int?): Boolean = n != null && n > FLASH
}

/** Estilo «Al flash» + intentos de una ascension de escuela, siempre coherentes entre si. */
data class StyleAttempts(val flash: Boolean, val attempts: Int?)

/** Enlace entre el estilo «Al flash» y los intentos: tocar 1 marca «Al flash» y marcar «Al flash» pone 1. Nada viene preseleccionado. */
object AttemptsStyleLink {

    /** Se toca un numero de intentos (null = se quita la seleccion). 1 marca flash; cualquier otro lo desmarca. */
    fun pick(current: StyleAttempts, picked: Int?): StyleAttempts {
        val n = Attempts.clean(picked)
        return when {
            n == null -> StyleAttempts(flash = false, attempts = null)
            n == Attempts.FLASH -> StyleAttempts(flash = true, attempts = Attempts.FLASH)
            else -> StyleAttempts(flash = false, attempts = n)
        }
    }

    /** Se marca o desmarca «Al flash»: marcarlo pone 1 intento; desmarcarlo quita el 1 (un 2 o mas se queda). */
    fun setFlash(current: StyleAttempts, on: Boolean): StyleAttempts =
        if (on) StyleAttempts(flash = true, attempts = Attempts.FLASH)
        else StyleAttempts(flash = false, attempts = current.attempts?.takeIf { it != Attempts.FLASH })
}
