package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.domain.repository.AdminRepository

/** Admin: ¿tiene este usuario el asistente de IA activado a mano? (solo su interruptor, no el global ni ser admin). */
class GetUserAssistantAccessUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String): Boolean = repository.getUserAssistantAccess(uid)
}

/** Admin: activa o desactiva el asistente de IA para un usuario. Devuelve el estado que quedó guardado. */
class SetUserAssistantAccessUseCase(private val repository: AdminRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String, enabled: Boolean): Boolean =
        repository.setUserAssistantAccess(uid, enabled)
}
