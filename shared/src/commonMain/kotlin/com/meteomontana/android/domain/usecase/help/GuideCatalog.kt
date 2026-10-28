package com.meteomontana.android.domain.usecase.help

/** Un punto de una guía: qué elemento de la pantalla se señala (`target`, lo registra la UI) y qué se cuenta de él. */
data class GuideStep(val target: String, val title: String, val body: String)

/** Recorrido corto de UNA pantalla (o de una pestaña). La clave coincide con la de la ayuda de texto cuando existe. */
data class Guide(val key: String, val steps: List<GuideStep>)

/**
 * Guías por pantalla ("¿Te enseño esta pantalla?"): máximo [MAX_STEPS] puntos cada una, ES y EN. Viven aquí (no en cada
 * app) para que iOS y Android cuenten lo mismo con las mismas palabras. La UI solo tiene que registrar cada `target`
 * (siempre con el prefijo `<clave>.`).
 *
 * Regla para elegir los puntos de una pantalla: 1) lo que más se usa, 2) lo que está escondido y nadie descubriría,
 * 3) lo que suele confundir. Si hay más de 3, se quita alguno: una guía larga es otro tour que nadie lee.
 *
 * La ficha de escuela tiene una guía general (`detail`) y una por pestaña (`detail_map`, `detail_blocks`,
 * `detail_weather`, `detail_notes`).
 */
object GuideCatalog {

    const val MAX_STEPS = 3

    /** Claves de todas las guías (para tests y para saber qué pantallas tienen guía). */
    val KEYS: List<String> = listOf(
        "schools", "detail", "detail_map", "detail_blocks", "detail_weather", "detail_notes",
        "weather", "community", "meetups", "profile"
    )

    /** `lang`: "es" o "en" (cualquier otro valor cae a español). `null` si esa pantalla no tiene guía. */
    fun byKey(key: String, lang: String): Guide? = byKey(key, lang, emptySet())

    /**
     * Igual, quitando los puntos cuyo `target` está en [hidden]: lo usa la app que todavía NO tiene esa función (p. ej.
     * la cámara de «¿qué piedra es esta?» llega antes a iOS), para no señalar un botón que no existe.
     */
    fun byKey(key: String, lang: String, hidden: Set<String>): Guide? {
        val steps = ((if (lang.lowercase().startsWith("en")) EN else ES)[key] ?: return null)
            .filterNot { it.target in hidden }
        return if (steps.isEmpty()) null else Guide(key, steps)
    }

    private val ES: Map<String, List<GuideStep>> = mapOf(
        "schools" to listOf(
            GuideStep("schools.score", "La nota de hoy",
                "Este número (0–100) dice si hoy se puede escalar en esa escuela. Cuanto más alto y más verde, mejor."),
            GuideStep("schools.filters", "Afina la lista",
                "FILTROS elige distancia, roca y estilo. El chip HOY abre los días: marca hasta 5 para comparar un tramo y ver dónde llueve."),
            GuideStep("schools.map", "Todo en un mapa",
                "Este icono enseña todas las escuelas en el mapa, coloreadas por su nota de hoy.")
        ),
        "detail" to listOf(
            GuideStep("detail.hoy", "Hoy, de un vistazo",
                "SÍ o NO, la nota y el estado de la roca. Tócala para ver las próximas 16 horas y por qué sale esa nota."),
            GuideStep("detail.tabs", "Cuatro pestañas",
                "MAPA: sectores, parkings y piedras. BLOQUES: busca vías por grado, sector o beta. TIEMPO: los próximos días. NOTAS: lo que cuenta la comunidad."),
            GuideStep("detail.photo", "¿Qué piedra es esta?",
                "Haz una foto a una piedra (o elige una de la galería) y te digo cuál de esta escuela es. También desde casa, con fotos sin ubicación.")
        ),
        "detail_map" to listOf(
            GuideStep("detail_map.places", "Sectores y parkings",
                "S = sector, P = parking. Toca uno y el mapa vuela hasta él."),
            GuideStep("detail_map.orientation", "Orientación",
                "Filtra las piedras por hacia dónde miran, para buscar sol o sombra."),
            GuideStep("detail_map.propose", "¿Falta algo?",
                "Con PROPONER añades una piedra, un parking o un sector. Un admin lo revisa antes de publicarlo.")
        ),
        "detail_blocks" to listOf(
            GuideStep("detail_blocks.search", "Busca por nombre",
                "Escribe el nombre de una vía, un bloque o un sector."),
            GuideStep("detail_blocks.filters", "Grado y beta",
                "FILTROS: uno o varios grados, y las vías con vídeo de beta para +1,70 o −1,70 (o las dos)."),
            GuideStep("detail_blocks.podium", "Las más repetidas",
                "El podio son las 3 vías que más gente ha hecho. Debajo, el resto por sectores: toca un sector para abrirlo.")
        ),
        "detail_weather" to listOf(
            GuideStep("detail_weather.heat", "El día de un vistazo",
                "La barra de colores va hora a hora: verde, se puede escalar; rojo, mejor no."),
            GuideStep("detail_weather.now", "Ahora mismo",
                "Humedad, viento, lluvia de las últimas 24 horas y nubes."),
            GuideStep("detail_weather.days", "Los próximos días",
                "Toca un día para ver su detalle hora a hora.")
        ),
        "detail_notes" to listOf(
            GuideStep("detail_notes.order", "Las más útiles primero",
                "Ordena las notas por votos. Vota ▲ o ▼ las que te sirvan."),
            GuideStep("detail_notes.write", "Aporta tu nota",
                "Cuenta lo que sepas de la escuela (acceso, parking, restricciones) y añade una foto si quieres.")
        ),
        "weather" to listOf(
            GuideStep("weather.toggle", "Tiempo y radar",
                "Aquí cambias entre la previsión (TIEMPO) y el radar de lluvia en directo (RADAR)."),
            GuideStep("weather.chips", "Tu sitio o tus favoritas",
                "Cambia entre tu ubicación y cada una de tus escuelas favoritas."),
            GuideStep("weather.compare", "Compáralas",
                "La tabla compara el índice medio de cada favorita para los próximos días.")
        ),
        "community" to listOf(
            GuideStep("community.tabs", "Tres vistas",
                "EXPLORAR: toda la comunidad. SIGUIENDO: solo la gente que sigues. MÍAS: lo que has publicado."),
            GuideStep("community.ranking", "Ranking",
                "El trofeo abre el ranking de quienes más aportan al catálogo."),
            GuideStep("community.post", "Reacciona y abre la piedra",
                "Toca el corazón o el bocadillo para reaccionar, y la foto o el nombre de la vía para abrir su piedra en la escuela.")
        ),
        "meetups" to listOf(
            GuideStep("meetups.create", "Crea una quedada",
                "Con + eliges escuela, días, privacidad y plazas, y la gente se une."),
            GuideStep("meetups.alert", "Alertas",
                "La campana te avisa cuando alguien crea una quedada en los días, la escuela o la distancia que te interesan."),
            GuideStep("meetups.filters", "Filtra",
                "FILTROS deja solo las quedadas de ciertos días, distancia o tipo de grupo, y se guardan para la próxima vez.")
        ),
        "profile" to listOf(
            GuideStep("profile.diary", "Tu diario",
                "Toca BLOQUES, VÍAS o ESCUELAS para ver lo que has hecho. El grado máximo se calcula solo."),
            GuideStep("profile.stats", "Tus estadísticas",
                "Tu pirámide de grados, tu objetivo del año, tus logros y cuánto has escalado cada mes."),
            GuideStep("profile.follows", "Seguidores",
                "Toca Seguidores o Siguiendo para ver y gestionar a la gente que sigues.")
        )
    )

    private val EN: Map<String, List<GuideStep>> = mapOf(
        "schools" to listOf(
            GuideStep("schools.score", "Today's score",
                "This number (0–100) tells you if you can climb at that crag today. The higher and greener, the better."),
            GuideStep("schools.filters", "Narrow the list",
                "FILTERS picks distance, rock and style. The TODAY chip opens the days: pick up to 5 to compare a range and see where it rains."),
            GuideStep("schools.map", "Everything on a map",
                "This icon shows all crags on the map, coloured by today's score.")
        ),
        "detail" to listOf(
            GuideStep("detail.hoy", "Today at a glance",
                "YES or NO, the score and the rock's condition. Tap it to see the next 16 hours and why the score is what it is."),
            GuideStep("detail.tabs", "Four tabs",
                "MAP: sectors, car parks and boulders. BOULDERS: find routes by grade, sector or beta. WEATHER: the next days. NOTES: what the community says."),
            GuideStep("detail.photo", "Which boulder is this?",
                "Take a photo of a boulder (or pick one from your gallery) and I'll tell you which one of this crag it is. Works from home too, with photos without location.")
        ),
        "detail_map" to listOf(
            GuideStep("detail_map.places", "Sectors and car parks",
                "S = sector, P = car park. Tap one and the map flies to it."),
            GuideStep("detail_map.orientation", "Aspect",
                "Filter boulders by the way they face, to look for sun or shade."),
            GuideStep("detail_map.propose", "Something missing?",
                "PROPOSE adds a boulder, a car park or a sector. An admin reviews it before it goes live.")
        ),
        "detail_blocks" to listOf(
            GuideStep("detail_blocks.search", "Search by name",
                "Type the name of a route, a boulder or a sector."),
            GuideStep("detail_blocks.filters", "Grade and beta",
                "FILTERS: one or more grades, and routes with a beta video for +1.70 m or −1.70 m (or both)."),
            GuideStep("detail_blocks.podium", "Most repeated",
                "The podium is the 3 routes most people have done. Below, the rest by sector: tap a sector to open it.")
        ),
        "detail_weather" to listOf(
            GuideStep("detail_weather.heat", "The day at a glance",
                "The colour bar runs hour by hour: green, climb; red, better not."),
            GuideStep("detail_weather.now", "Right now",
                "Humidity, wind, rain over the last 24 hours and cloud."),
            GuideStep("detail_weather.days", "The next days",
                "Tap a day to see it hour by hour.")
        ),
        "detail_notes" to listOf(
            GuideStep("detail_notes.order", "Most useful first",
                "Sort notes by votes. Vote ▲ or ▼ on the ones that help."),
            GuideStep("detail_notes.write", "Add your note",
                "Share what you know about the crag (access, parking, restrictions) and add a photo if you like.")
        ),
        "weather" to listOf(
            GuideStep("weather.toggle", "Weather and radar",
                "Switch here between the forecast (WEATHER) and the live rain radar (RADAR)."),
            GuideStep("weather.chips", "Your spot or your favourites",
                "Switch between your location and each of your favourite crags."),
            GuideStep("weather.compare", "Compare them",
                "The table compares each favourite's average score for the coming days.")
        ),
        "community" to listOf(
            GuideStep("community.tabs", "Three views",
                "EXPLORE: the whole community. FOLLOWING: only people you follow. MINE: what you've posted."),
            GuideStep("community.ranking", "Ranking",
                "The trophy opens the ranking of the people who contribute most to the catalogue."),
            GuideStep("community.post", "React and open the boulder",
                "Tap the heart or the bubble to react, and the photo or the route name to open its boulder at the crag.")
        ),
        "meetups" to listOf(
            GuideStep("meetups.create", "Create a meetup",
                "With + you choose the crag, days, privacy and places, and people join."),
            GuideStep("meetups.alert", "Alerts",
                "The bell tells you when someone creates a meetup on the days, crag or distance you care about."),
            GuideStep("meetups.filters", "Filter",
                "FILTERS keeps only meetups on certain days, distance or group type, and remembers them for next time.")
        ),
        "profile" to listOf(
            GuideStep("profile.diary", "Your logbook",
                "Tap BOULDERS, ROUTES or CRAGS to see what you've done. Your max grade is worked out for you."),
            GuideStep("profile.stats", "Your statistics",
                "Your grade pyramid, your yearly goal, your achievements and how much you climbed each month."),
            GuideStep("profile.follows", "Followers",
                "Tap Followers or Following to see and manage the people you follow.")
        )
    )
}
