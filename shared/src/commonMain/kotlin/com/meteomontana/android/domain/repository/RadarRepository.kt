package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.RadarFrames

interface RadarRepository {
    /** Timeline de frames del radar (compuesto España o regional según coords). */
    suspend fun getFrames(
        lat: Double? = null,
        lon: Double? = null,
        hours: Int = 2,
        date: String? = null
    ): RadarFrames

    /** PNG Cumbre de un frame (bytes listos para decodificar). */
    suspend fun getFramePng(radar: String, ts: String): ByteArray
}
