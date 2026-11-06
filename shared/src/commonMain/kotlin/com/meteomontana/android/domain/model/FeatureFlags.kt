package com.meteomontana.android.domain.model

/** Claves de las funciones que el servidor puede apagar sin publicar una versión nueva. */
object FeatureKey {
    const val ASSISTANT = "assistant"
    const val FEED_PUBLISH = "feed_publish"
    const val MEETUP_CREATE = "meetup_create"
    const val GUIDE_PDF = "guide_pdf"
}

/** Estado de un interruptor: encendido o no, y el aviso (ya traducido por el servidor) si lo hay. */
data class FeatureFlagState(val enabled: Boolean, val message: String? = null)

/**
 * Interruptores de funciones. Una clave que no conocemos cuenta como ENCENDIDA: si el servidor
 * nunca nos ha dicho nada de una función, no hay motivo para taparla.
 */
data class FeatureFlags(val flags: Map<String, FeatureFlagState> = emptyMap()) {

    fun isEnabled(key: String): Boolean = flags[key]?.enabled ?: true

    fun messageFor(key: String): String? = flags[key]?.message?.takeIf { it.isNotBlank() }

    companion object {
        /** Todo encendido: lo que se asume si nunca se ha podido preguntar al servidor. */
        val ALL_ON = FeatureFlags()
    }
}
