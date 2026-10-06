package com.meteomontana.android.domain.util

/**
 * Qué se escala en una escuela, según el campo `style` del catálogo
 * ("Bloque", "Vía" o "Bloque,Vía"). Lógica pura: mosquetón = vías, crashpad =
 * bloques, los dos = ambos (Álvaro, 2026-10-06). Espejo de `SchoolKind` en
 * `SchoolKindIcons.swift` (iOS).
 */
data class SchoolKind(val hasRoutes: Boolean, val hasBoulders: Boolean) {

    val isEmpty: Boolean get() = !hasRoutes && !hasBoulders

    /** Solo una de las dos: útil donde se muestra UN resultado (una vía o un
     *  bloque); en escuelas mixtas no se sabe cuál es y no se pinta nada. */
    val isSingle: Boolean get() = hasRoutes != hasBoulders

    companion object {
        fun from(style: String?): SchoolKind {
            // Sin tildes ni mayúsculas: "Vía" == "via" == "VIA".
            val s = (style ?: "").lowercase().replace('í', 'i')
            return SchoolKind(
                hasRoutes = s.contains("via") || s.contains("route"),
                hasBoulders = s.contains("bloque") || s.contains("boulder")
            )
        }
    }
}
