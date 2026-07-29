package com.meteomontana.android

/**
 * Resultado de parsear una URL compartida `/s/...`:
 * el destino de navegación y, si es una quedada, los datos de invitación.
 */
data class DeepLinkParse(
    val target: DeepLinkTarget,
    val meetupInviteId: String? = null,
    val meetupInviteToken: String? = null
)

/**
 * Parseo PURO de los enlaces compartidos `/s/{tipo}/{id...}` — sin Android,
 * para poder testearlo (antes vivía inline en `MainActivity.consumeIntentExtras`
 * y no había forma de cubrirlo; el `/s/q` con token decide el acceso a quedadas).
 *
 * Tipos: `e` escuela · `v` vía (escuela/via) · `q` quedada (?i=token) ·
 *        `u` perfil · `p` publicación del feed.
 */
object DeepLinkParser {

    /**
     * @param segments los `pathSegments` de la URI.
     * @param query    resuelve parámetros de query (p.ej. `i` → token de invitación).
     * @return el destino, o `null` si no es un `/s/...` reconocible o le faltan segmentos.
     */
    fun parse(segments: List<String>, query: (String) -> String? = { null }): DeepLinkParse? {
        if (segments.firstOrNull() != "s") return null
        return when (segments.getOrNull(1)) {
            "q" -> segments.getOrNull(2)?.let { id ->
                DeepLinkParse(DeepLinkTarget("meetup", id), meetupInviteId = id, meetupInviteToken = query("i"))
            }
            "e" -> segments.getOrNull(2)?.let { DeepLinkParse(DeepLinkTarget("school", it)) }
            "v" -> {
                val school = segments.getOrNull(2)
                val line = segments.getOrNull(3)
                if (school != null && line != null) DeepLinkParse(DeepLinkTarget("via", "$school|$line")) else null
            }
            "u" -> segments.getOrNull(2)?.let { DeepLinkParse(DeepLinkTarget("user", it)) }
            "p" -> segments.getOrNull(2)?.let { DeepLinkParse(DeepLinkTarget("feed_post", it)) }
            else -> null
        }
    }
}
