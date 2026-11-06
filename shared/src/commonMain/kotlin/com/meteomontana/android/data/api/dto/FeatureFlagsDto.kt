package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

/** GET /api/feature-flags (público). */
@Serializable
data class FeatureFlagsDto(
    val flags: Map<String, FeatureFlagDto> = emptyMap(),
    val refreshAfterSeconds: Int = 300
)

@Serializable
data class FeatureFlagDto(val enabled: Boolean = true, val message: String? = null)

/** Cuerpo del 503 cuando se intenta usar una función apagada. */
@Serializable
data class FeatureDisabledBody(val code: String? = null, val feature: String? = null, val message: String? = null)
