package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SectorHourDto(val hour: Int, val score: Int, val sun: Boolean = false)

@Serializable
data class SectorHoursDto(
    val sectorId: String,
    val name: String = "",
    val aspect: String = "",
    val hours: List<SectorHourDto> = emptyList(),
    val bestFrom: Int? = null,
    val bestTo: Int? = null,
    val bestScore: Int? = null,
)

@Serializable
data class SectorHoursResponseDto(val date: String = "", val sectors: List<SectorHoursDto> = emptyList())
