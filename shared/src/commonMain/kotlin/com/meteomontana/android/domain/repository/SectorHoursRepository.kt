package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.SectorHours

/** Mejor hora por sector de una escuela (ordenados del mejor al peor). */
interface SectorHoursRepository {
    /** [date] "yyyy-MM-dd"; null = hoy. */
    suspend fun get(schoolId: String, date: String?): List<SectorHours>
}
