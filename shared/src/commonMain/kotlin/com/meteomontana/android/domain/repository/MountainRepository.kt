package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.MountainBulletin

interface MountainRepository {
    /** Boletín de montaña AEMET del macizo de esas coords, o null si no hay. */
    suspend fun getBulletin(lat: Double, lon: Double, day: Int = 0): MountainBulletin?
}
