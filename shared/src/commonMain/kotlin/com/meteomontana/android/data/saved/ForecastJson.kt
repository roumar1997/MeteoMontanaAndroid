package com.meteomontana.android.data.saved

import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDto
import com.meteomontana.android.domain.model.Forecast
import kotlinx.serialization.json.Json

/**
 * Serialización de Forecast a JSON usando kotlinx-serialization (KMP-friendly).
 * Reutiliza el ForecastDto ya existente (mismo shape que la respuesta del backend).
 *
 * commonMain → compila para Android e iOS sin cambios.
 */
object ForecastJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun encode(forecast: Forecast): String =
        json.encodeToString(ForecastDto.serializer(), forecast.toDto())

    fun decode(s: String): Forecast =
        json.decodeFromString(ForecastDto.serializer(), s).toDomain()
}
