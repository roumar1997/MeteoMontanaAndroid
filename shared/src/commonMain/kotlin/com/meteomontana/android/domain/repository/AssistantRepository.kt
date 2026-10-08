package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.AssistantAnswer
import com.meteomontana.android.domain.model.AssistantUnderstood

interface AssistantRepository {
    /**
     * @param previous lo entendido en el mensaje anterior, para refinar sin repetir.
     * @param schoolId escuela que el usuario tiene abierta, si la hay.
     */
    @Throws(Exception::class)
    suspend fun ask(
        text: String,
        previous: AssistantUnderstood?,
        lat: Double?,
        lon: Double?,
        schoolId: String?
    ): AssistantAnswer
}
