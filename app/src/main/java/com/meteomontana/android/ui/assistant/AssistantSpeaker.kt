package com.meteomontana.android.ui.assistant

import com.meteomontana.android.presentation.assistant.AssistantLabels
import com.meteomontana.android.presentation.assistant.AssistantPresenter
import com.meteomontana.android.presentation.assistant.*
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.meteomontana.android.util.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cuándo contesta el asistente en voz alta. Se guarda en el móvil (menú «⋯» del chat). Espejo de `AssistantSpeechMode`.
 */
enum class AssistantSpeechMode {
    /** Si preguntas hablando, contesta hablando (lo natural en la roca, con las manos ocupadas). */
    WHEN_SPOKEN,
    ALWAYS,
    NEVER;

    /** ¿Hay que leer esta respuesta? [spokenQuestion] = la pregunta se dictó. */
    fun shouldSpeak(spokenQuestion: Boolean): Boolean = when (this) {
        ALWAYS -> true
        WHEN_SPOKEN -> spokenQuestion
        NEVER -> false
    }
}

/**
 * Lee en voz alta las respuestas con las voces del propio móvil: gratis, sin enviar nada fuera. Espejo de
 * `AssistantSpeaker`. Una sola voz a la vez: leer otra respuesta corta la anterior.
 */
@Singleton
class AssistantSpeaker @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences("assistant_speech", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<AssistantSpeechMode> = _mode.asStateFlow()

    /** Id del mensaje que se está leyendo (el altavoz de esa burbuja se pinta activo). */
    private val _speakingId = MutableStateFlow<Long?>(null)
    val speakingId: StateFlow<Long?> = _speakingId.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    /** Lo pedido antes de que el motor de voz esté listo (el primer uso tarda un instante en arrancar). */
    private var waiting: Pair<String, Long>? = null

    fun setMode(mode: AssistantSpeechMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
        if (mode == AssistantSpeechMode.NEVER) stop()
    }

    fun speak(text: String, id: Long) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        stop()
        _speakingId.value = id
        val engine = tts
        if (engine != null && ready) {
            say(engine, clean, id)
        } else {
            waiting = clean to id
            if (engine == null) start()
        }
    }

    fun stop() {
        waiting = null
        runCatching { tts?.stop() }
        _speakingId.value = null
    }

    fun toggle(text: String, id: Long) {
        if (_speakingId.value == id) stop() else speak(text, id)
    }

    private fun start() {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            val engine = tts
            if (!ready || engine == null) {
                waiting = null
                _speakingId.value = null
                return@TextToSpeech
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { _speakingId.value = null }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { _speakingId.value = null }
            })
            waiting?.let { (text, id) -> waiting = null; say(engine, text, id) }
        }
    }

    private fun say(engine: TextToSpeech, text: String, id: Long) {
        val english = AppLanguage.effective(context).lowercase().startsWith("en")
        engine.language = if (english) Locale.UK else Locale("es", "ES")
        // Android corta las frases de más de ~4000 caracteres; lo que se lee es siempre un resumen corto.
        engine.speak(text.take(MAX_SPOKEN), TextToSpeech.QUEUE_FLUSH, null, "assistant-$id")
    }

    private fun readMode(): AssistantSpeechMode =
        runCatching { AssistantSpeechMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }.getOrDefault(AssistantSpeechMode.WHEN_SPOKEN)

    private companion object {
        const val KEY_MODE = "mode"
        const val MAX_SPOKEN = 3500
    }
}

/**
 * Lo que se lee en voz alta de una respuesta: el texto de la burbuja y un resumen corto de la tarjeta, nunca listas largas.
 * Espejo de `AssistantPresenter.speech(for:)`.
 */
internal fun spokenText(m: AssistantMessage, lang: String): String {
    val en = lang.lowercase().startsWith("en")
    val parts = mutableListOf(m.text)
    m.recommendation?.schools?.firstOrNull()?.let { first ->
        val score = first.combinedScore
        parts += when {
            score != null && en -> "The best is ${first.name}, $score out of 100."
            score != null -> "La mejor es ${first.name}, $score sobre 100."
            en -> "The first one is ${first.name}."
            else -> "La primera es ${first.name}."
        }
    }
    if (m.hits.isNotEmpty()) parts += m.hits.take(3).joinToString(", ") { it.lineName ?: it.blockName } + "."
    m.photoMatches.firstOrNull()?.let { parts += it.blockName + "." }
    return parts.joinToString(" ")
}
