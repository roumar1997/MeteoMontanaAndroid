package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

/** Respuesta de GET /api/radar/frames — timeline + esquinas del PNG. */
@Serializable
data class RadarFramesDto(
    val radar: String,
    val bounds: RadarBoundsDto,
    val frames: List<RadarFrameRefDto>
)

@Serializable
data class RadarBoundsDto(
    val north: Double,
    val west: Double,
    val south: Double,
    val east: Double
)

@Serializable
data class RadarFrameRefDto(
    val ts: String,          // yyyyMMdd-HHmm — clave para GET /radar/frame/{radar}/{ts}
    val capturedAt: String   // ISO local Europe/Madrid
)
