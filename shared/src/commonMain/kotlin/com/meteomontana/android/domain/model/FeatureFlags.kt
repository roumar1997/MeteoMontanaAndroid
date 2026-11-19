package com.meteomontana.android.domain.model

/** Claves de las funciones que el servidor puede apagar sin publicar una versión nueva. */
object FeatureKey {
    const val ASSISTANT = "assistant"
    const val FEED_PUBLISH = "feed_publish"
    const val MEETUP_CREATE = "meetup_create"
    const val GUIDE_PDF = "guide_pdf"
    /** Rocodromos. A DIFERENCIA del resto, si el servidor no la manda cuenta como APAGADA (ver [FeatureFlags.isGymsEnabled]). */
    const val GYMS = "gyms_enabled"
}

/** Estado de un interruptor: encendido o no, y el aviso (ya traducido por el servidor) si lo hay. */
data class FeatureFlagState(val enabled: Boolean, val message: String? = null)

/**
 * Interruptores de funciones. Una clave que no conocemos cuenta como ENCENDIDA: si el servidor
 * nunca nos ha dicho nada de una función, no hay motivo para taparla.
 */
data class FeatureFlags(val flags: Map<String, FeatureFlagState> = emptyMap()) {

    fun isEnabled(key: String): Boolean = flags[key]?.enabled ?: true

    /**
     * Rocodromos: apagado salvo que el servidor diga expresamente que si. Sin red, sin copia o con la copia
     * caducada (72 h) la funcion NO sale: se enciende solo cuando las tiendas ya tienen la version.
     */
    val isGymsEnabled: Boolean get() = flags[FeatureKey.GYMS]?.enabled ?: false

    fun messageFor(key: String): String? = flags[key]?.message?.takeIf { it.isNotBlank() }

    companion object {
        /** Todo encendido: lo que se asume si nunca se ha podido preguntar al servidor. */
        val ALL_ON = FeatureFlags()
    }
}
