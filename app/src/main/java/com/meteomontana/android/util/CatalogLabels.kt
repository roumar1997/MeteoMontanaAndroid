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
        else -> ROCK_EXTRA[raw.trim()]?.takeIf { english() } ?: raw
    }

    private fun english() = AppText.get(R.string.lang_code) == "en"

    private val ROCK_EXTRA = mapOf(
        "Cuarcita" to "Quartzite",
        "Volcánica" to "Volcanic",
        "Caliza / calcoarenita" to "Limestone / calcarenite",
        "Otra / muro barrenado" to "Other / drilled wall"
    )

    private val REGIONS = mapOf(
        "Cataluña" to "Catalonia",
        "Castilla y León" to "Castile and León",
        "Aragón" to "Aragon",
        "Andalucía" to "Andalusia",
        "Comunidad Valenciana" to "Valencian Community",
        "Comunidad de Madrid" to "Community of Madrid",
        "Galicia" to "Galicia",
        "Asturias" to "Asturias",
        "País Vasco" to "Basque Country",
        "Navarra" to "Navarre",
        "Canarias" to "Canary Islands",
        "Extremadura" to "Extremadura",
        "Islas Baleares" to "Balearic Islands",
        "Cantabria" to "Cantabria",
        "Región de Murcia" to "Region of Murcia",
        "Castilla-La Mancha" to "Castilla-La Mancha",
        "Nueva Aquitania" to "Nouvelle-Aquitaine",
        "Comunidad Autónoma de Cantabria" to "Cantabria",
        "Leon" to "León",
        "Burgos" to "Burgos",
        "Jaén" to "Jaén"
    )

    /** Comunidad autónoma / región del catálogo ("Comunidad de Madrid"); admite varias separadas por " / ". */
    fun region(raw: String): String {
        if (!english()) return raw
        return raw.split(" / ").joinToString(" / ") { part -> REGIONS[part.trim()] ?: part.trim() }
    }

    /** Orientación ("N", "SO"…): los códigos son los de la API; solo cambia cómo se muestran. */
    fun aspect(code: String): String = when (code.trim().uppercase()) {
        "SO" -> AppText.get(R.string.aspect_sw)
        "O" -> AppText.get(R.string.aspect_w)
        "NO" -> AppText.get(R.string.aspect_nw)
        else -> code
    }

    /** Estilo de una escuela: "Bloque", "Vía" o "Bloque y vía" (según el catálogo). */
    fun style(raw: String): String = raw.split(",").joinToString(", ") { one ->
        when (one.trim().lowercase()) {
            "bloque" -> AppText.get(R.string.w_boulder)
            "vía", "via" -> AppText.get(R.string.w_route)
            else -> one.trim()
        }
    }
}
