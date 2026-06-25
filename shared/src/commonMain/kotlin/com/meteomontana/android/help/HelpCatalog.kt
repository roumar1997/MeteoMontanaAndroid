package com.meteomontana.android.help

/**
 * Catálogo ÚNICO de textos de ayuda contextual, compartido entre Android e iOS
 * (mismo copy en ambas plataformas, se edita en un solo sitio). Cada pantalla
 * tiene un [HelpTopic] con una intro y una lista de "qué puedes hacer aquí".
 *
 * Lo consume el botón "?" de cada pantalla (HelpSheet en Android / iOS).
 */
/** [icon] = nombre semántico que cada plataforma mapea a su set de iconos
 *  (Material en Android, SF Symbols en iOS). Ver helpIcon() en cada plataforma. */
data class HelpItem(val title: String, val body: String, val icon: String = "info")

data class HelpTopic(
    val key: String,
    val title: String,
    val intro: String,
    val items: List<HelpItem>
)

object HelpCatalog {

    val SCHOOLS = HelpTopic(
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
    )

    val DETAIL = HelpTopic(
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
    )

    val PROPOSE = HelpTopic(
        key = "propose",
        title = "Proponer en el mapa",
        intro = "Puedes añadir cosas al mapa de la escuela. Lo revisa un admin antes de publicarse.",
        items = listOf(
            HelpItem("Elige qué proponer", "Parking, piedra, sector o corregir la posición de algo existente.", icon = "plus"),
            HelpItem("Fija la posición", "Cuando salga \"Pulsa en el mapa\", toca el punto exacto donde está.", icon = "map"),
            HelpItem("Piedra con vías", "Añade una o varias fotos (caras) y, en cada una, sus vías (nombre, grado, tipo). Dibuja cada vía arrastrando sobre la foto.", icon = "edit"),
            HelpItem("Muros largos", "Elige geometría MURO, traza el muro tocando puntos en el mapa y elige el sentido de numeración.", icon = "wall")
        )
    )

    val PROFILE = HelpTopic(
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
    )

    val JOURNAL = HelpTopic(
        key = "journal",
        title = "Tu diario",
        intro = "Aquí se guardan las vías y bloques que marcas como hechos.",
        items = listOf(
            HelpItem("Cómo se añade", "Marca el ✓ de una vía dentro de su piedra (en el detalle de la escuela) y aparece aquí.", icon = "tick"),
            HelpItem("Filtra", "Cambia entre BLOQUES y VÍAS arriba.", icon = "filter"),
            HelpItem("Abre la piedra", "Toca una entrada para abrir su piedra en la escuela.", icon = "map"),
            HelpItem("Borrar", "Puedes borrar tus propias entradas.", icon = "edit")
        )
    )

    val CHAT = HelpTopic(
        key = "chat",
        title = "Mensajes",
        intro = "Habla con otras personas de la comunidad. Puedes escribir a quien sigues o te sigue, y a perfiles públicos.",
        items = listOf(
            HelpItem("Nuevo mensaje / grupo", "Con los iconos de arriba inicias una conversación nueva o creas un grupo.", icon = "chat"),
            HelpItem("Gestiona una conversación", "Desliza una conversación: a la izquierda para borrarla, a la derecha para marcarla como no leída.", icon = "edit"),
            HelpItem("Responder a un mensaje", "Dentro del chat, desliza una burbuja a la derecha para responder citándola.", icon = "reply"),
            HelpItem("Sin conexión", "En conversaciones que ya existen puedes escribir sin internet; el mensaje se envía al reconectar.", icon = "wifioff")
        )
    )

    val WEATHER = HelpTopic(
        key = "weather",
        title = "Tiempo",
        intro = "La previsión de tu ubicación y la comparación de tus escuelas favoritas.",
        items = listOf(
            HelpItem("Tu ubicación o una favorita", "Con los chips de arriba cambias entre tu ubicación y cada escuela favorita.", icon = "star"),
            HelpItem("Comparar favoritas", "La tabla compara el índice medio por día de tus favoritas.", icon = "compare"),
            HelpItem("Ver un día", "Toca un día para ver su detalle por horas.", icon = "calendar")
        )
    )

    private val all = listOf(SCHOOLS, DETAIL, PROPOSE, PROFILE, JOURNAL, CHAT, WEATHER)

    fun byKey(key: String): HelpTopic? = all.firstOrNull { it.key == key }
}
