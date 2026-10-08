package com.meteomontana.android.domain.usecase.assistant

import com.meteomontana.android.domain.model.AssistantAnswer
import com.meteomontana.android.domain.model.AssistantUnderstood
import com.meteomontana.android.domain.repository.AssistantRepository

/**
 * Pregunta al asistente. El texto se recorta aquí a 200 caracteres (el servidor
 * hace lo mismo): ni hace falta más ni se paga por tokens de más.
 */
class AskAssistantUseCase(private val repository: AssistantRepository) {

    @Throws(Exception::class)
    suspend operator fun invoke(
        text: String,
        previous: AssistantUnderstood? = null,
        lat: Double? = null,
        lon: Double? = null,
        schoolId: String? = null
    ): AssistantAnswer =
        repository.ask(text.trim().take(MAX_TEXT), previous, lat, lon, schoolId)

    companion object {
        const val MAX_TEXT = 200
    }
}
