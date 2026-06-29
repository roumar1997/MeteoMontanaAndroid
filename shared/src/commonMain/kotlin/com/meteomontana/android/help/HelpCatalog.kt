package com.meteomontana.android.help

data class HelpItem(val title: String, val body: String, val icon: String = "info")

data class HelpTopic(
    val key: String,
    val title: String,
    val intro: String,
    val items: List<HelpItem>
)

object HelpCatalog {

    fun byKey(key: String, lang: String = "es"): HelpTopic? {
        val catalog = if (lang.startsWith("en")) EN else ES
        return catalog.firstOrNull { it.key == key }
    }

    // ── Spanish (default) ─────────────────────────────────────────────────

    private val ES = listOf(
        HelpTopic(
            key = "schools",
            title = "Escuelas",
            intro = "El listado de escuelas de escalada con su índice del día. El índice (0-100) resume si hoy es buen día para escalar: verde = bueno, ámbar = regular, rojo = malo.",
            items = listOf(
                HelpItem("Filtra", "Por distancia, estilo (bloque/vía), tipo de roca, y ordena por mejor score o más cercanas.", icon = "filter"),
                HelpItem("Compara varios días", "Toca los chips de día (hasta 5) para ver un tramo: el número pasa a ser la media y se marcan los días de lluvia.", icon = "calendar"),
                HelpItem("Favoritas", "Toca la estrella de una escuela para guardarla como favorita. Funciona sin conexión.", icon = "star"),
                HelpItem("Comparar escuelas", "Mantén pulsada una escuela para entrar en modo comparar (hasta 3); abajo aparece COMPARAR.", icon = "compare"),
                HelpItem("Mapa", "Pulsa VER MAPA para ver las escuelas en el mapa, coloreadas por su índice.", icon = "map"),
                HelpItem("Proponer una escuela", "Con + Enviar escuela puedes proponer una escuela que falte.", icon = "plus")
            )
        ),
        HelpTopic(
            key = "detail",
            title = "Detalle de la escuela",
            intro = "La previsión para escalar, el mapa con las piedras y las notas de la comunidad.",
            items = listOf(
                HelpItem("El índice y por qué", "Arriba ves el veredicto (SÍ/NO) y el índice /100. Toca \"¿Por qué este índice?\" para ver los factores (temperatura, humedad, viento, lluvia, secado de la roca…).", icon = "info"),
                HelpItem("Próximas horas y días", "Desliza la fila de \"Próximas 16 horas\" y mira los próximos 7 días con el mejor día marcado.", icon = "clock"),
                HelpItem("Guardar offline", "El icono de descarga guarda la escuela (previsión + mapa + piedras) para verla sin conexión.", icon = "download"),
                HelpItem("Mapa de la escuela", "Markers: P = parking, Z = sector, piedra y muros. Toca un sector (Z) para mostrar/ocultar sus piedras. Toca una piedra para ver sus vías.", icon = "map"),
                HelpItem("Marcar una vía hecha", "Dentro de una piedra, toca el círculo (○ → ✓) de una vía para apuntarla en tu diario. Funciona sin conexión.", icon = "tick"),
                HelpItem("Proponer / corregir", "Con + PROPONER añades parking, piedra, sector o corriges una posición; un admin lo revisa.", icon = "plus")
            )
        ),
        HelpTopic(
            key = "propose",
            title = "Proponer en el mapa",
            intro = "Puedes añadir cosas al mapa de la escuela. Lo revisa un admin antes de publicarse.",
            items = listOf(
                HelpItem("Elige qué proponer", "Parking, piedra, sector o corregir la posición de algo existente.", icon = "plus"),
                HelpItem("Fija la posición", "Cuando salga \"Pulsa en el mapa\", toca el punto exacto donde está.", icon = "map"),
                HelpItem("Piedra con vías", "Añade una o varias fotos (caras) y, en cada una, sus vías (nombre, grado, tipo). Dibuja cada vía arrastrando sobre la foto.", icon = "edit"),
                HelpItem("Muros largos", "Elige geometría MURO, traza el muro tocando puntos en el mapa y elige el sentido de numeración.", icon = "wall")
            )
        ),
        HelpTopic(
            key = "profile",
            title = "Tu cuenta",
            intro = "Tu perfil, tu diario de escalada y tus ajustes.",
            items = listOf(
                HelpItem("Tu diario", "Toca BLOQUES, VÍAS o ESCUELAS para ver lo que has marcado como hecho. El grado máximo se calcula solo desde el diario.", icon = "book"),
                HelpItem("Añadir a mano", "Con + AÑADIR BLOQUE registras una vía/bloque sin pasar por el mapa.", icon = "plus"),
                HelpItem("Seguidores", "Toca Seguidores o Siguiendo para ver y gestionar a quién sigues.", icon = "person"),
                HelpItem("Escuelas guardadas", "Tus escuelas descargadas para usar sin conexión.", icon = "download"),
                HelpItem("Alerta de tiempo", "Configura un aviso que compara tus escuelas (o por cercanía) los días que elijas.", icon = "bell"),
                HelpItem("Editar perfil", "Cambia tu foto, @usuario, bio y si tu perfil es público.", icon = "edit")
            )
        ),
        HelpTopic(
            key = "journal",
            title = "Tu diario",
            intro = "Aquí se guardan las vías y bloques que marcas como hechos.",
            items = listOf(
                HelpItem("Cómo se añade", "Marca el ✓ de una vía dentro de su piedra (en el detalle de la escuela) y aparece aquí.", icon = "tick"),
                HelpItem("Filtra", "Cambia entre BLOQUES y VÍAS arriba.", icon = "filter"),
                HelpItem("Abre la piedra", "Toca una entrada para abrir su piedra en la escuela.", icon = "map"),
                HelpItem("Borrar", "Puedes borrar tus propias entradas.", icon = "edit")
            )
        ),
        HelpTopic(
            key = "chat",
            title = "Mensajes",
            intro = "Habla con otras personas de la comunidad. Puedes escribir a quien sigues o te sigue, y a perfiles públicos.",
            items = listOf(
                HelpItem("Nuevo mensaje / grupo", "Con los iconos de arriba inicias una conversación nueva o creas un grupo.", icon = "chat"),
                HelpItem("Gestiona una conversación", "Desliza una conversación: a la izquierda para borrarla, a la derecha para marcarla como no leída.", icon = "edit"),
                HelpItem("Responder a un mensaje", "Dentro del chat, desliza una burbuja a la derecha para responder citándola.", icon = "reply"),
                HelpItem("Sin conexión", "En conversaciones que ya existen puedes escribir sin internet; el mensaje se envía al reconectar.", icon = "wifioff")
            )
        ),
        HelpTopic(
            key = "weather",
            title = "Tiempo",
            intro = "La previsión de tu ubicación y la comparación de tus escuelas favoritas.",
            items = listOf(
                HelpItem("Tu ubicación o una favorita", "Con los chips de arriba cambias entre tu ubicación y cada escuela favorita.", icon = "star"),
                HelpItem("Comparar favoritas", "La tabla compara el índice medio por día de tus favoritas.", icon = "compare"),
                HelpItem("Ver un día", "Toca un día para ver su detalle por horas.", icon = "calendar")
            )
        ),
        HelpTopic(
            key = "meetups",
            title = "Quedadas",
            intro = "Organiza salidas a escalar con la comunidad. Crea una quedada, elige escuela y días, y la gente se une.",
            items = listOf(
                HelpItem("Crear una quedada", "Pulsa + para crear: elige escuela, días, privacidad (abierta, seguidos/seguidores o no mixto) y límite de plazas.", icon = "plus"),
                HelpItem("Mapa de quedadas", "Toca \"VER MAPA DE QUEDADAS\" para ver en qué escuelas hay quedadas activas. Toca una para filtrar.", icon = "map"),
                HelpItem("Filtrar por días", "Usa los chips de día para ver solo quedadas de los días que te interesan (puedes elegir varios).", icon = "calendar"),
                HelpItem("Filtrar por distancia", "Con los chips de distancia ves solo quedadas de escuelas cerca de ti.", icon = "filter"),
                HelpItem("Chat directo", "Si ya estás unido a una quedada, al tocarla entras directo al chat del grupo (no al detalle).", icon = "chat"),
                HelpItem("Alertas", "La campanita activa las alertas: elige los días que te interesan y te avisamos cuando alguien cree una quedada.", icon = "bell"),
                HelpItem("Filtros guardados", "Los filtros que elijas (tipo de grupo, distancia) se guardan y se aplican la próxima vez que abras Quedadas. Así no tienes que configurarlos cada vez.", icon = "star")
            )
        ),
        HelpTopic(
            key = "meetup_detail",
            title = "Detalle de quedada",
            intro = "Toda la información de la quedada: escuela, días con su índice meteo, participantes y chat.",
            items = listOf(
                HelpItem("Índice por día", "Cada día muestra un badge de color con el índice para escalar (verde = bueno, rojo = malo).", icon = "info"),
                HelpItem("Cómo llegar", "Toca CÓMO LLEGAR para abrir la ruta en Google Maps hasta la escuela.", icon = "map"),
                HelpItem("Descripción", "Si eres el organizador puedes añadir detalles (material, nivel, punto de encuentro) con el lápiz.", icon = "edit"),
                HelpItem("Unirse / salir", "Con UNIRSE entras a la quedada y al chat. Puedes salir en cualquier momento.", icon = "person"),
                HelpItem("Chat del grupo", "Toca el icono de chat arriba a la derecha para ir al chat.", icon = "chat")
            )
        )
    )

    // ── English ───────────────────────────────────────────────────────────

    private val EN = listOf(
        HelpTopic(
            key = "schools",
            title = "Schools",
            intro = "The list of climbing schools with today's index. The index (0-100) summarizes whether today is a good day to climb: green = good, amber = fair, red = bad.",
            items = listOf(
                HelpItem("Filter", "By distance, style (boulder/route), rock type, and sort by best score or nearest.", icon = "filter"),
                HelpItem("Compare days", "Tap the day chips (up to 5) to see a range: the number becomes the average and rainy days are marked.", icon = "calendar"),
                HelpItem("Favorites", "Tap a school's star to save it as a favorite. Works offline.", icon = "star"),
                HelpItem("Compare schools", "Long-press a school to enter compare mode (up to 3); COMPARE appears at the bottom.", icon = "compare"),
                HelpItem("Map", "Tap VIEW MAP to see the schools on the map, colored by their index.", icon = "map"),
                HelpItem("Suggest a school", "With + Submit school you can suggest a school that's missing.", icon = "plus")
            )
        ),
        HelpTopic(
            key = "detail",
            title = "School detail",
            intro = "The climbing forecast, the map with boulders and the community notes.",
            items = listOf(
                HelpItem("The index and why", "At the top you see the verdict (YES/NO) and the index /100. Tap \"Why this index?\" to see the factors (temperature, humidity, wind, rain, rock drying…).", icon = "info"),
                HelpItem("Upcoming hours and days", "Scroll the \"Next 16 hours\" row and check the next 7 days with the best day highlighted.", icon = "clock"),
                HelpItem("Save offline", "The download icon saves the school (forecast + map + boulders) to use without internet.", icon = "download"),
                HelpItem("School map", "Markers: P = parking, Z = sector, boulder and walls. Tap a sector (Z) to show/hide its boulders. Tap a boulder to see its routes.", icon = "map"),
                HelpItem("Mark a route done", "Inside a boulder, tap the circle (○ → ✓) of a route to log it in your journal. Works offline.", icon = "tick"),
                HelpItem("Propose / correct", "With + PROPOSE you can add parking, boulder, sector or correct a position; an admin reviews it.", icon = "plus")
            )
        ),
        HelpTopic(
            key = "propose",
            title = "Propose on the map",
            intro = "You can add things to the school map. An admin reviews it before publishing.",
            items = listOf(
                HelpItem("Choose what to propose", "Parking, boulder, sector or correct the position of something existing.", icon = "plus"),
                HelpItem("Set the position", "When \"Tap on the map\" appears, tap the exact spot where it is.", icon = "map"),
                HelpItem("Boulder with routes", "Add one or more photos (faces) and, on each one, its routes (name, grade, type). Draw each route by dragging on the photo.", icon = "edit"),
                HelpItem("Long walls", "Choose WALL geometry, trace the wall by tapping points on the map and choose the numbering direction.", icon = "wall")
            )
        ),
        HelpTopic(
            key = "profile",
            title = "Your account",
            intro = "Your profile, climbing journal and settings.",
            items = listOf(
                HelpItem("Your journal", "Tap BOULDERS, ROUTES or SCHOOLS to see what you've marked as done. The max grade is calculated from the journal.", icon = "book"),
                HelpItem("Add manually", "With + ADD BOULDER you can log a route/boulder without going through the map.", icon = "plus"),
                HelpItem("Followers", "Tap Followers or Following to see and manage who you follow.", icon = "person"),
                HelpItem("Saved schools", "Your schools downloaded to use offline.", icon = "download"),
                HelpItem("Weather alert", "Set up a notification that compares your schools (or by proximity) on the days you choose.", icon = "bell"),
                HelpItem("Edit profile", "Change your photo, @username, bio and whether your profile is public.", icon = "edit")
            )
        ),
        HelpTopic(
            key = "journal",
            title = "Your journal",
            intro = "Here you'll find the routes and boulders you've marked as done.",
            items = listOf(
                HelpItem("How to add", "Tap the ✓ of a route inside its boulder (in the school detail) and it appears here.", icon = "tick"),
                HelpItem("Filter", "Switch between BOULDERS and ROUTES at the top.", icon = "filter"),
                HelpItem("Open the boulder", "Tap an entry to open its boulder in the school.", icon = "map"),
                HelpItem("Delete", "You can delete your own entries.", icon = "edit")
            )
        ),
        HelpTopic(
            key = "chat",
            title = "Messages",
            intro = "Chat with other people in the community. You can message people you follow, who follow you, and public profiles.",
            items = listOf(
                HelpItem("New message / group", "Use the icons at the top to start a new conversation or create a group.", icon = "chat"),
                HelpItem("Manage a conversation", "Swipe a conversation: left to delete, right to mark as unread.", icon = "edit"),
                HelpItem("Reply to a message", "Inside the chat, swipe a bubble to the right to reply quoting it.", icon = "reply"),
                HelpItem("Offline", "In conversations that already exist you can write without internet; the message sends when you reconnect.", icon = "wifioff")
            )
        ),
        HelpTopic(
            key = "weather",
            title = "Weather",
            intro = "The forecast for your location and the comparison of your favorite schools.",
            items = listOf(
                HelpItem("Your location or a favorite", "Use the chips at the top to switch between your location and each favorite school.", icon = "star"),
                HelpItem("Compare favorites", "The table compares the average index by day of your favorites.", icon = "compare"),
                HelpItem("View a day", "Tap a day to see its hourly detail.", icon = "calendar")
            )
        ),
        HelpTopic(
            key = "meetups",
            title = "Meetups",
            intro = "Organize climbing sessions with the community. Create a meetup, choose a school and days, and people join.",
            items = listOf(
                HelpItem("Create a meetup", "Tap + to create: choose school, days, privacy (open, followers or women-only) and participant limit.", icon = "plus"),
                HelpItem("Meetup map", "Tap \"VIEW MEETUP MAP\" to see which schools have active meetups. Tap one to filter.", icon = "map"),
                HelpItem("Filter by days", "Use the day chips to see only meetups on the days you're interested in (you can select multiple).", icon = "calendar"),
                HelpItem("Filter by distance", "With the distance chips you see only meetups at schools near you.", icon = "filter"),
                HelpItem("Direct chat", "If you've already joined a meetup, tapping it takes you straight to the group chat (not the detail).", icon = "chat"),
                HelpItem("Alerts", "The bell activates alerts: choose the days you're interested in and we'll notify you when someone creates a meetup.", icon = "bell"),
                HelpItem("Saved filters", "The filters you choose (group type, distance) are saved and applied next time you open Meetups. No need to configure them every time.", icon = "star")
            )
        ),
        HelpTopic(
            key = "meetup_detail",
            title = "Meetup detail",
            intro = "All the meetup info: school, days with climbing index, participants and chat.",
            items = listOf(
                HelpItem("Index per day", "Each day shows a colored badge with the climbing index (green = good, red = bad).", icon = "info"),
                HelpItem("Directions", "Tap DIRECTIONS to open the route in Google Maps to the school.", icon = "map"),
                HelpItem("Description", "If you're the organizer you can add details (gear, level, meeting point) with the pencil.", icon = "edit"),
                HelpItem("Join / leave", "With JOIN you enter the meetup and its chat. You can leave at any time.", icon = "person"),
                HelpItem("Group chat", "Tap the chat icon at the top right to go to the chat.", icon = "chat")
            )
        )
    )
}
