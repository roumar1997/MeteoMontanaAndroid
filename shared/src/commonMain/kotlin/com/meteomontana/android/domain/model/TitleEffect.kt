package com.meteomontana.android.domain.model

/**
 * Efecto de título (aureola animada alrededor de la foto) tal como lo describe el servidor en GET /api/title-effects.
 * Las apps lo pintan con estos datos: un efecto nuevo llega sin publicar versión.
 */
data class TitleEffectSpec(
    val key: String,
    val name: String,
    /** [FACE_PHOTO]: miran a la foto. [ORBIT]: tangentes al círculo, la parte de abajo hacia la foto, sentido horario. */
    val orientation: String,
    val figureCount: Int,
    val sprites: List<TitleEffectSprite>,
    /** Degradado del aro, «#RRGGBB». */
    val ringColors: List<String>,
    val glowColor: String,
    val highlightColor: String,
    val particleColor: String,
    /** [SPARKLE] estrellitas o [EMBER] brasas. */
    val particleStyle: String,
    val motion: TitleEffectMotion,
) {
    val orbits: Boolean get() = orientation == ORBIT
    val embers: Boolean get() = particleStyle == EMBER

    companion object {
        const val FACE_PHOTO = "FACE_PHOTO"
        const val ORBIT = "ORBIT"
        const val SPARKLE = "SPARKLE"
        const val EMBER = "EMBER"
    }
}

/** url = CDN (null si es integrado); asset = imagen empaquetada en la app, respaldo sin red. */
data class TitleEffectSprite(val url: String?, val asset: String?, val facesRight: Boolean)

/** Órbita y tamaño en radios de la foto; speed en rad/s; ángulos y tamaño de la vista quieta (fotos pequeñas). */
data class TitleEffectMotion(
    val orbit: Float,
    val size: Float,
    val speed: Float,
    val staticAngles: List<Float>,
    val staticSize: Float,
)

/** Catálogo de efectos. Una clave que no está (app vieja, servidor caído la 1ª vez) = sin efecto. */
data class TitleEffectCatalog(val effects: List<TitleEffectSpec> = emptyList()) {

    fun find(key: String?): TitleEffectSpec? {
        val k = key?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        return effects.firstOrNull { it.key == k }
    }

    companion object {
        /** Los integrados en la app: se ven aunque nunca se haya podido preguntar al servidor. */
        val BUILT_IN = TitleEffectCatalog(listOf(
            TitleEffectSpec("FAIRIES", "Hadas", TitleEffectSpec.FACE_PHOTO, 4,
                (1..4).map { TitleEffectSprite(null, "effect_fairy$it", true) },
                listOf("#D4A21C", "#FFECAA", "#D4A21C", "#FFECAA"), "#D4A21C", "#FFECAA", "#FFECAA",
                TitleEffectSpec.SPARKLE, TitleEffectMotion(1.72f, 1.25f, 0.42f, listOf(-2.3f, 0.3f), 0.62f)),
            TitleEffectSpec("DRAGONS", "Dragones", TitleEffectSpec.FACE_PHOTO, 2,
                listOf(TitleEffectSprite(null, "effect_dragon1", true), TitleEffectSprite(null, "effect_dragon2", false)),
                listOf("#C4281C", "#FFAA5A", "#C4281C", "#FFAA5A"), "#C4281C", "#FFAA5A", "#FF7828",
                TitleEffectSpec.EMBER, TitleEffectMotion(1.62f, 1.85f, 0.33f, listOf(-2.2f), 0.95f)),
            TitleEffectSpec("LOLA", "Furgo Lola", TitleEffectSpec.ORBIT, 2,
                listOf(TitleEffectSprite(null, "effect_van1", true), TitleEffectSprite(null, "effect_van2", false)),
                listOf("#7CCBF5", "#1E9BE0", "#0E6FB0", "#1E9BE0"), "#1E9BE0", "#7CCBF5", "#CFEFFF",
                TitleEffectSpec.SPARKLE, TitleEffectMotion(1.5f, 0.62f, 0.45f, listOf(-2.2f), 0.45f)),
        ))
    }
}
