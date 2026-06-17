package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.dto.WeekendAlertDto

/**
 * Casos de uso de la "Alerta de tiempo" (GET/PUT /api/me/weekend-alert).
 * Trabajan directamente con [WeekendAlertDto] (igual que el ViewModel de
 * Android), porque la pantalla mapea 1:1 ese payload.
 */
class GetWeekendAlertUseCase(private val api: KtorProfileApi) {
    @Throws(Exception::class)
    suspend operator fun invoke(): WeekendAlertDto = api.getWeekendAlert()
}

class UpdateWeekendAlertUseCase(private val api: KtorProfileApi) {
    @Throws(Exception::class)
    suspend operator fun invoke(req: WeekendAlertDto): WeekendAlertDto = api.updateWeekendAlert(req)
}
