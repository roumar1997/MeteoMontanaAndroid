package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorMountainApi
import com.meteomontana.android.data.api.MountainBulletinDto
import com.meteomontana.android.data.api.MountainSpotDto
import com.meteomontana.android.domain.model.MountainBulletin
import com.meteomontana.android.domain.model.MountainSpot
import com.meteomontana.android.domain.repository.MountainRepository

class KtorMountainRepository(private val api: KtorMountainApi) : MountainRepository {
    override suspend fun getBulletin(lat: Double, lon: Double, day: Int): MountainBulletin? =
        api.getBulletin(lat, lon, day)?.toDomain()
}

private fun MountainBulletinDto.toDomain() = MountainBulletin(
    area = area, areaName = areaName, day = day, texts = texts,
    spots = spots.map { it.toDomain() }
)

private fun MountainSpotDto.toDomain() = MountainSpot(
    nombre = nombre, altitud = altitud, minima = minima, maxima = maxima
)
