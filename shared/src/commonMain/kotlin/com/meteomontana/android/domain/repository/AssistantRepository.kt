package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.AssistantAnswer
import com.meteomontana.android.domain.model.AssistantUnderstood

interface AssistantRepository {
    /**
     * @param previous lo entendido en el mensaje anterior, para refinar sin repetir.
     * @param schoolId escuela que el usuario tiene abierta, si la hay.
     * @param homeLat casa guardada en el móvil (o null); el servidor solo la usa si la frase dice "cerca de casa".
     */
    @Throws(Exception::class)
    suspend fun ask(
        text: String,
        previous: AssistantUnderstood?,
        lat: Double?,
        lon: Double?,
        schoolId: String?,
        homeLat: Double?,
        homeLon: Double?
    ): AssistantAnswer

    /** ¿Puede esta cuenta usar el asistente? (ya combinado por el servidor: admin, global o activado a mano). */
    @Throws(Exception::class)
    suspend fun hasAccess(): Boolean
}
