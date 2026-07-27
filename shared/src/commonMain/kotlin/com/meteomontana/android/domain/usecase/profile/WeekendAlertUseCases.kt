package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.domain.model.WeekendAlert
import com.meteomontana.android.domain.repository.ProfileRepository

/**
 * Casos de uso de la "Alerta de tiempo" (GET/PUT /api/me/weekend-alert).
 * Trabajan con el modelo de dominio [WeekendAlert]; el DTO del backend se
 * queda en la capa de datos.
 */
class GetWeekendAlertUseCase(private val repo: ProfileRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): WeekendAlert = repo.getWeekendAlert()
}

class UpdateWeekendAlertUseCase(private val repo: ProfileRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(alert: WeekendAlert): WeekendAlert =
        repo.updateWeekendAlert(alert)
}
