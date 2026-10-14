package com.meteomontana.android.ui.assistant

/**
 * Cuándo el modo "hablar" decide que has terminado y manda la pregunta solo. Es lógica de tiempos pura (sin micrófono)
 * para poder probarla sin dispositivo. Espejo de `AssistantSpeechPolicy.swift`.
 *
 *  - Después de oír algo, ~2,5 s de silencio = has acabado → se envía. (Con 1,5 s cortaba al pensar a mitad de frase:
 *    Álvaro, 2026-10-08, "para de escuchar antes de tiempo".)
 *  - Si no se oye nada en unos segundos → se abandona sin enviar (no se gasta cupo).
 *  - Tope de duración, por si hay ruido de fondo que mantiene vivo el reconocimiento.
 */
class AssistantSpeechPolicy(
    private val silenceAfterTextMs: Long = 2_500,
    private val noSpeechTimeoutMs: Long = 8_000,
    private val maxDurationMs: Long = 25_000
) {
    enum class Decision {
        KEEP_LISTENING,
        /** Hay texto y has dejado de hablar (o se alcanzó el tope): enviarlo. */
        SEND,
        /** No se oyó nada: parar sin enviar. */
        GIVE_UP
    }

    /**
     * @param startedAtMs cuándo empezó a escuchar.
     * @param lastTextAtMs cuándo CAMBIÓ por última vez lo reconocido (null si aún nada).
     */
    fun decide(nowMs: Long, startedAtMs: Long, lastTextAtMs: Long?, hasText: Boolean): Decision {
        if (nowMs - startedAtMs >= maxDurationMs) return if (hasText) Decision.SEND else Decision.GIVE_UP
        if (!hasText || lastTextAtMs == null) {
            return if (nowMs - startedAtMs >= noSpeechTimeoutMs) Decision.GIVE_UP else Decision.KEEP_LISTENING
        }
        return if (nowMs - lastTextAtMs >= silenceAfterTextMs) Decision.SEND else Decision.KEEP_LISTENING
    }
}
