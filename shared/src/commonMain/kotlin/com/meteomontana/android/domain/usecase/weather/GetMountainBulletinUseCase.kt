package com.meteomontana.android.domain.usecase.weather

import com.meteomontana.android.domain.model.MountainBulletin
import com.meteomontana.android.domain.repository.MountainRepository

/** Boletín de montaña AEMET del macizo de una escuela (null si no hay). */
class GetMountainBulletinUseCase(private val repository: MountainRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(lat: Double, lon: Double, day: Int = 0): MountainBulletin? =
        repository.getBulletin(lat, lon, day)
}
