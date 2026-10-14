package com.meteomontana.android.ui.assistant

import android.content.Context
import com.meteomontana.android.domain.model.AssistantUnderstood
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Conversación del asistente guardada EN EL MÓVIL (nunca en un servidor) para que no se pierda si el usuario cierra la
 * app un rato. Espejo de `AssistantTranscriptStore.swift`.
 *
 * Decisiones (Álvaro, 2026-10-08):
 *  - Solo unas horas: pasadas [TTL_MS] la conversación empieza de cero.
 *  - Se guardan también las tarjetas de resultados (para poder comprobar lo que se enseñó), como texto (`payload`, lo
 *    genera `AssistantSnapshotCodec` en shared). Como llevan pronóstico, cada mensaje guarda su hora y la pantalla
 *    avisa de cuánto hace.
 *  - Es de UNA cuenta: si cambia el usuario, no se restaura (y se borra al cerrar sesión).
 */
@Serializable
data class StoredAssistantConversation(
    val uid: String,
    val savedAt: Long,
    val messages: List<Message>,
    val previous: Understood? = null
) {
    @Serializable
    data class Message(
        val isUser: Boolean,
        val text: String,
        val chips: List<String> = emptyList(),
        /** Tarjetas del mensaje (escuelas, sectores, vías) como texto; null si no tenía. */
        val payload: String? = null,
        val createdAt: Long
    )

    /** Lo último que entendió el servidor, para poder seguir refinando ("ahora solo 7a"). Campos nuevos con valor por
     *  defecto: una conversación guardada con una versión anterior sigue leyéndose. */
    @Serializable
    data class Understood(
        val intent: String,
        val dateFrom: String? = null,
        val dateTo: String? = null,
        val gradeMin: String? = null,
        val gradeMax: String? = null,
        val discipline: String? = null,
        val rockTypes: List<String> = emptyList(),
        val orientations: List<String> = emptyList(),
        val maxDistanceKm: Double? = null,
        val q: String? = null,
        val schoolMention: String? = null,
        val sectorMention: String? = null,
        val sun: String? = null,
        val dayPart: String? = null,
        val schoolMentions: List<String> = emptyList(),
        val topRated: Boolean = false,
        val minStars: Int? = null,
        val hoursAhead: Int? = null,
        val weatherTopic: String? = null,
        val useMyLocation: Boolean = false,
        val beta: String? = null,
        val startType: String? = null,
        val withTopo: Boolean = false,
        val noRain: Boolean = false,
        val mineTopic: String? = null,
        val year: Int? = null,
        val notDone: Boolean = false,
        val action: String? = null,
        val mostLines: Boolean = false
    ) {
        fun toDomain() = AssistantUnderstood(
            intent = intent, dateFrom = dateFrom, dateTo = dateTo, gradeMin = gradeMin, gradeMax = gradeMax,
            discipline = discipline, rockTypes = rockTypes, orientations = orientations, maxDistanceKm = maxDistanceKm,
            q = q, schoolMention = schoolMention, sectorMention = sectorMention, sun = sun, dayPart = dayPart,
            schoolMentions = schoolMentions, topRated = topRated, minStars = minStars, hoursAhead = hoursAhead,
            weatherTopic = weatherTopic, useMyLocation = useMyLocation, beta = beta, startType = startType,
            withTopo = withTopo, noRain = noRain, mineTopic = mineTopic, year = year, notDone = notDone,
            action = action, mostLines = mostLines
        )

        companion object {
            fun from(u: AssistantUnderstood) = Understood(
                intent = u.intent, dateFrom = u.dateFrom, dateTo = u.dateTo, gradeMin = u.gradeMin, gradeMax = u.gradeMax,
                discipline = u.discipline, rockTypes = u.rockTypes, orientations = u.orientations,
                maxDistanceKm = u.maxDistanceKm, q = u.q, schoolMention = u.schoolMention, sectorMention = u.sectorMention,
                sun = u.sun, dayPart = u.dayPart, schoolMentions = u.schoolMentions, topRated = u.topRated,
                minStars = u.minStars, hoursAhead = u.hoursAhead, weatherTopic = u.weatherTopic,
                useMyLocation = u.useMyLocation, beta = u.beta, startType = u.startType, withTopo = u.withTopo,
                noRain = u.noRain, mineTopic = u.mineTopic, year = u.year, notDone = u.notDone, action = u.action,
                mostLines = u.mostLines
            )
        }
    }
}

/** Dónde se guarda el texto de la conversación (SharedPreferences en la app, un mapa en las pruebas). */
interface TranscriptStorage {
    fun read(): String?
    fun write(text: String)
    fun clear()
}

class SharedTranscriptStorage(context: Context) : TranscriptStorage {
    private val prefs = context.applicationContext.getSharedPreferences("assistant", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString(KEY, null)
    override fun write(text: String) = prefs.edit().putString(KEY, text).apply()
    override fun clear() = prefs.edit().remove(KEY).apply()

    private companion object { const val KEY = "assistant_conversation_v1" }
}

class AssistantTranscriptStore(private val storage: TranscriptStorage) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun save(conversation: StoredAssistantConversation) {
        val trimmed = conversation.copy(messages = conversation.messages.takeLast(MAX_MESSAGES))
        runCatching { storage.write(json.encodeToString(trimmed)) }
    }

    /** La conversación guardada si es de esta cuenta y no ha caducado; si no, null (y se borra lo caducado). */
    fun load(uid: String, nowMs: Long = System.currentTimeMillis()): StoredAssistantConversation? {
        val text = storage.read() ?: return null
        val c = runCatching { json.decodeFromString<StoredAssistantConversation>(text) }.getOrNull() ?: return null
        if (nowMs - c.savedAt >= TTL_MS) { clear(); return null }
        return if (c.uid == uid) c else null
    }

    fun clear() = storage.clear()

    companion object {
        /** Cuánto se conserva: pasado esto, la conversación se descarta. */
        const val TTL_MS = 12L * 3600 * 1000
        /** Máximo de mensajes guardados (los más recientes). */
        const val MAX_MESSAGES = 30
    }
}
