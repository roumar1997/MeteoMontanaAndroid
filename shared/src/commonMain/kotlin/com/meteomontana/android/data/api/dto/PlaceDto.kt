package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlaceDto(
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0
)
