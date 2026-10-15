package com.meteomontana.android.domain.model

/**
 * Altura en grupos, nunca en cifras (para agrupar los votos de grado: un bloque no se siente igual con 1,60 m que con
 * 1,90 m). [UNSPECIFIED] = prefiere no decirlo (se guarda para no volver a preguntar). Privada: solo la ve su dueño.
 *
 * [apiValue] es lo que habla el servidor (TALL | SHORT | NONE). El caso se llama UNSPECIFIED y no NONE porque en Swift
 * `.none` se confundiría con `Optional.none` al comparar un `HeightGroup?`.
 */
enum class HeightGroup(val apiValue: String) {
    /** Más de 1,70 m. */
    TALL("TALL"),
    /** Menos de 1,70 m. */
    SHORT("SHORT"),
    /** Prefiere no decirlo. */
    UNSPECIFIED("NONE");

    companion object {
        /** Interpreta el texto del servidor; null si no es un grupo conocido (servidor más nuevo que la app). */
        fun parse(raw: String?): HeightGroup? = entries.firstOrNull { it.apiValue == raw?.trim()?.uppercase() }
    }
}
