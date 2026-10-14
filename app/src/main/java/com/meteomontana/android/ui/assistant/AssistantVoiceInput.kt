package com.meteomontana.android.ui.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.meteomontana.android.util.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dictado del asistente con el reconocimiento de voz del sistema. Espejo de `AssistantVoiceInput.swift`.
 *
 * Android corta cada segmento tras un silencio corto; aquí se vuelve a escuchar hasta que [AssistantSpeechPolicy]
 * decide que has terminado (≈2,5 s de silencio tras oír algo → se envía solo) o que no hay nada que oír (se abandona sin
 * enviar, para no gastar cupo). El permiso de micrófono lo pide la pantalla ([hasPermission]).
 *
 * Hay que crearlo y usarlo desde el hilo principal.
 */
class AssistantVoiceInput(
    private val context: Context,
    private val scope: CoroutineScope,
    private val policy: AssistantSpeechPolicy = AssistantSpeechPolicy()
) {
    var isListening by mutableStateOf(false)
        private set
    /** Se esperó y no se oyó nada: la pantalla avisa "No te he oído". */
    var heardNothing by mutableStateOf(false)
        private set

    /** Lo reconocido hasta ahora (para pintarlo en la caja de texto). */
    var onText: (String) -> Unit = {}
    /** Al callar: lo dicho, para enviarlo. */
    var onFinished: (String) -> Unit = {}

    private var recognizer: SpeechRecognizer? = null
    private var ticker: Job? = null
    private var startedAt = 0L
    private var lastTextAt: Long? = null
    private var accumulated = ""
    private var current = ""
    private val text: String get() = "$accumulated $current".trim()

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    fun toggle() { if (isListening) stop() else start() }

    fun start() {
        if (isListening || !hasPermission() || !isAvailable()) return
        heardNothing = false
        accumulated = ""
        current = ""
        lastTextAt = null
        startedAt = System.currentTimeMillis()
        isListening = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply { setRecognitionListener(listener) }
        listen()
        ticker = scope.launch {
            while (isListening) {
                delay(250)
                when (policy.decide(System.currentTimeMillis(), startedAt, lastTextAt, text.isNotEmpty())) {
                    AssistantSpeechPolicy.Decision.KEEP_LISTENING -> Unit
                    AssistantSpeechPolicy.Decision.SEND -> { val said = text; stop(); onFinished(said) }
                    AssistantSpeechPolicy.Decision.GIVE_UP -> { stop(); heardNothing = true }
                }
            }
        }
    }

    /** Para sin enviar (lo reconocido se queda en la caja para editarlo). */
    fun stop() {
        isListening = false
        ticker?.cancel()
        ticker = null
        recognizer?.run { runCatching { cancel() }; runCatching { destroy() } }
        recognizer = null
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (AppLanguage.effective(context) == AppLanguage.EN) "en-US" else "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun best(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    /** Android cierra el segmento solo; si aún no toca parar, se vuelve a escuchar. */
    private fun relisten() {
        if (!isListening) return
        scope.launch { delay(120); if (isListening) runCatching { recognizer?.cancel(); listen() } }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val s = best(partialResults)
            if (s.isEmpty() || s == current) return
            current = s
            lastTextAt = System.currentTimeMillis()
            onText(text)
        }

        override fun onResults(results: Bundle?) {
            val s = best(results)
            if (s.isNotEmpty()) {
                accumulated = "$accumulated $s".trim()
                lastTextAt = System.currentTimeMillis()
                onText(text)
            }
            current = ""
            relisten()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> relisten()
                // Permiso, red, ocupado…: no se puede seguir. Lo ya reconocido queda en la caja.
                else -> { stop(); if (text.isEmpty()) heardNothing = true }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
