package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

/** GET /api/title-effects (público). Todo con valores por defecto: un campo nuevo del servidor no rompe nada. */
@Serializable
data class TitleEffectsDto(
    val effects: List<TitleEffectDto> = emptyList(),
    val refreshAfterSeconds: Int = 3600,
)

@Serializable
data class TitleEffectDto(
    val key: String,
    val name: String = "",
    val orientation: String = "FACE_PHOTO",
    val figureCount: Int = 2,
    val sprites: List<TitleEffectSpriteDto> = emptyList(),
    val ringColors: List<String> = emptyList(),
    val glowColor: String = "#FFFFFF",
    val highlightColor: String = "#FFFFFF",
    val particleColor: String = "#FFFFFF",
    val particleStyle: String = "SPARKLE",
    val motion: TitleEffectMotionDto = TitleEffectMotionDto(),
)

@Serializable
data class TitleEffectSpriteDto(val url: String? = null, val asset: String? = null, val facesRight: Boolean = true)

@Serializable
data class TitleEffectMotionDto(
    val orbit: Float = 1.6f,
    val size: Float = 1.2f,
    val speed: Float = 0.4f,
    val staticAngles: List<Float> = listOf(-2.2f),
    val staticSize: Float = 0.7f,
)
