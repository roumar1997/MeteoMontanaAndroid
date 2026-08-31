package com.meteomontana.android.domain.model

/** Timeline del radar + esquinas geográficas del PNG (modelo de dominio). */
data class RadarFrames(
    val radar: String,
    val bounds: RadarBounds,
    val frames: List<RadarFrameRef>
)

data class RadarBounds(
    val north: Double,
    val west: Double,
    val south: Double,
    val east: Double
)

data class RadarFrameRef(
    val ts: String,          // yyyyMMdd-HHmm
    val capturedAt: String   // ISO local Europe/Madrid
)
