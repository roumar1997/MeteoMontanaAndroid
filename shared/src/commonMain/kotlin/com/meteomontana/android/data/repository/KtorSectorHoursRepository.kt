package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorSectorHoursApi
import com.meteomontana.android.domain.model.SectorHour
import com.meteomontana.android.domain.model.SectorHours
import com.meteomontana.android.domain.repository.SectorHoursRepository

class KtorSectorHoursRepository(private val api: KtorSectorHoursApi) : SectorHoursRepository {
    override suspend fun get(schoolId: String, date: String?): List<SectorHours> =
        api.get(schoolId, date).sectors.map { s ->
            SectorHours(s.sectorId, s.name, s.aspect, s.hours.map { SectorHour(it.hour, it.score, it.sun) },
                s.bestFrom, s.bestTo, s.bestScore)
        }
}
