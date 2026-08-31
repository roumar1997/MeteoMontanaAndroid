package com.meteomontana.android.domain.usecase.radar

import com.meteomontana.android.domain.model.RadarFrames
import com.meteomontana.android.domain.repository.RadarRepository

class GetRadarFramesUseCase(private val repository: RadarRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        lat: Double? = null,
        lon: Double? = null,
        hours: Int = 2,
        date: String? = null
    ): RadarFrames = repository.getFrames(lat, lon, hours, date)
}

class GetRadarFramePngUseCase(private val repository: RadarRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(radar: String, ts: String): ByteArray =
        repository.getFramePng(radar, ts)
}
