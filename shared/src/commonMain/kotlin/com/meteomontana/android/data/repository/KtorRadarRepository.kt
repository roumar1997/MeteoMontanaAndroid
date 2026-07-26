package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorRadarApi
import com.meteomontana.android.data.api.dto.RadarBoundsDto
import com.meteomontana.android.data.api.dto.RadarFrameRefDto
import com.meteomontana.android.data.api.dto.RadarFramesDto
import com.meteomontana.android.domain.model.RadarBounds
import com.meteomontana.android.domain.model.RadarFrameRef
import com.meteomontana.android.domain.model.RadarFrames
import com.meteomontana.android.domain.repository.RadarRepository

class KtorRadarRepository(private val api: KtorRadarApi) : RadarRepository {
    override suspend fun getFrames(lat: Double?, lon: Double?, hours: Int, date: String?): RadarFrames =
        api.getFrames(lat, lon, hours, date).toDomain()

    override suspend fun getFramePng(radar: String, ts: String): ByteArray =
        api.getFramePng(radar, ts)
}

private fun RadarFramesDto.toDomain() = RadarFrames(
    radar = radar, bounds = bounds.toDomain(), frames = frames.map { it.toDomain() }
)

private fun RadarBoundsDto.toDomain() = RadarBounds(north = north, west = west, south = south, east = east)

private fun RadarFrameRefDto.toDomain() = RadarFrameRef(ts = ts, capturedAt = capturedAt)
