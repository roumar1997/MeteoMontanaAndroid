package com.meteomontana.android.util

import com.meteomontana.android.R

/**
 * Etiquetas de valores del CATÁLOGO que llegan del servidor en español
 * ("Caliza", "Bloque"…). El valor guardado y enviado a la API no cambia;
 * esto solo decide cómo se MUESTRA. Lo desconocido se muestra tal cual.
 */
object CatalogLabels {
    fun rock(raw: String): String = when (raw.trim().lowercase()) {
        "granito" -> AppText.get(R.string.rock_granite)
        "caliza" -> AppText.get(R.string.rock_limestone)
        "arenisca" -> AppText.get(R.string.rock_sandstone)
        "basalto" -> AppText.get(R.string.rock_basalt)
        "conglomerado" -> AppText.get(R.string.rock_conglomerate)
        "pizarra" -> AppText.get(R.string.rock_slate)
        else -> raw
    }

    /** Orientación ("N", "SO"…): los códigos son los de la API; solo cambia cómo se muestran. */
    fun aspect(code: String): String = when (code.trim().uppercase()) {
        "SO" -> AppText.get(R.string.aspect_sw)
        "O" -> AppText.get(R.string.aspect_w)
        "NO" -> AppText.get(R.string.aspect_nw)
        else -> code
    }

    /** Estilo de una escuela: "Bloque", "Vía" o "Bloque y vía" (según el catálogo). */
    fun style(raw: String): String = when (raw.trim().lowercase()) {
        "bloque" -> AppText.get(R.string.w_boulder)
        "vía", "via" -> AppText.get(R.string.w_route)
        else -> raw
    }
}
