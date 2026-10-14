package com.meteomontana.android.domain.usecase.help

/** Un punto de una guía: qué elemento de la pantalla se señala (`target`, lo registra la UI) y qué se cuenta de él. */
data class GuideStep(val target: String, val title: String, val body: String)

/** Recorrido corto de UNA pantalla. Las claves coinciden con las de la ayuda de texto (`HelpCatalog`): "schools", "detail". */
data class Guide(val key: String, val steps: List<GuideStep>)

/**
 * Guías por pantalla ("¿Te enseño esta pantalla?"): máximo [MAX_STEPS] puntos cada una, ES y EN. Viven aquí (no en cada
 * app) para que iOS y Android cuenten lo mismo con las mismas palabras. La UI solo tiene que registrar cada `target`.
 *
 * Regla para elegir los puntos de una pantalla: 1) lo que más se usa, 2) lo que está escondido y nadie descubriría,
 * 3) lo que suele confundir. Si hay más de 3, se quita alguno: una guía larga es otro tour que nadie lee.
 */
object GuideCatalog {

    const val MAX_STEPS = 3

    /** Claves de todas las guías (para tests y para saber qué pantallas tienen guía). */
    val KEYS: List<String> = listOf("schools", "detail")

    /** `lang`: "es" o "en" (cualquier otro valor cae a español). `null` si esa pantalla no tiene guía. */
    fun byKey(key: String, lang: String): Guide? {
        val en = lang.lowercase().startsWith("en")
        return when (key) {
            "schools" -> Guide(key, if (en) schoolsEn else schoolsEs)
            "detail" -> Guide(key, if (en) detailEn else detailEs)
            else -> null
        }
    }

    private val schoolsEs = listOf(
        GuideStep("schools.score", "La nota de hoy",
            "Este número (0–100) dice si hoy se puede escalar en esa escuela. Cuanto más alto y más verde, mejor."),
        GuideStep("schools.filters", "Afina la lista",
            "FILTROS elige distancia, roca y estilo. El chip HOY abre los días: marca hasta 5 para comparar un tramo y ver dónde llueve."),
        GuideStep("schools.map", "Todo en un mapa",
            "Este icono enseña todas las escuelas en el mapa, coloreadas por su nota de hoy.")
    )

    private val schoolsEn = listOf(
        GuideStep("schools.score", "Today's score",
            "This number (0–100) tells you if you can climb at that crag today. The higher and greener, the better."),
        GuideStep("schools.filters", "Narrow the list",
            "FILTERS picks distance, rock and style. The TODAY chip opens the days: pick up to 5 to compare a range and see where it rains."),
        GuideStep("schools.map", "Everything on a map",
            "This icon shows all crags on the map, coloured by today's score.")
    )

    private val detailEs = listOf(
        GuideStep("detail.hoy", "Hoy, de un vistazo",
            "SÍ o NO, la nota y el estado de la roca. Tócala para ver las próximas 16 horas y por qué sale esa nota."),
        GuideStep("detail.tabs", "Cuatro pestañas",
            "MAPA: sectores, parkings y piedras. BLOQUES: busca vías por grado, sector o beta. TIEMPO: los próximos días. NOTAS: lo que cuenta la comunidad."),
        GuideStep("detail.here", "Cuando estés en la roca",
            "Pulsa «Aquí» para que otros sepan que estás, y usa el chat para hablar con quien vaya a esa escuela.")
    )

    private val detailEn = listOf(
        GuideStep("detail.hoy", "Today at a glance",
            "YES or NO, the score and the rock's condition. Tap it to see the next 16 hours and why the score is what it is."),
        GuideStep("detail.tabs", "Four tabs",
            "MAP: sectors, car parks and boulders. BOULDERS: find routes by grade, sector or beta. WEATHER: the next days. NOTES: what the community says."),
        GuideStep("detail.here", "When you're at the crag",
            "Tap \"Here\" so others know you're there, and use the chat to talk to whoever is heading to that crag.")
    )
}
