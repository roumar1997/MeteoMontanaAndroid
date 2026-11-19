package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymSessionSource
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Contrato de docs/rocodromos/BACKEND-PENDIENTE.md. [date] es yyyy-MM-dd. Lo desconocido no tira la lista. */
@Serializable
data class GymSessionDto(
    val gymId: Long,
    val date: String,
    val source: String? = null
)

fun GymSessionDto.toDomainOrNull(): GymSession? =
    runCatching { GymSession(gymId, LocalDate.parse(date), GymSessionSource.parse(source)) }.getOrNull()

fun GymSession.toDto() = GymSessionDto(gymId = gymId, date = date.toString(), source = source.name)
