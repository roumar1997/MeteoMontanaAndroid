package com.meteomontana.android.domain.usecase.assistant

import com.meteomontana.android.domain.repository.AssistantRepository

/**
 * ¿Puedo usar el asistente de IA? El servidor ya combina las tres razones (ser admin, estar abierto a todos o que un
 * admin me lo haya activado), así que la app solo decide con esto si enseña la burbuja.
 */
class GetAssistantAccessUseCase(private val repository: AssistantRepository) {

    @Throws(Exception::class)
    suspend operator fun invoke(): Boolean = repository.hasAccess()
}
