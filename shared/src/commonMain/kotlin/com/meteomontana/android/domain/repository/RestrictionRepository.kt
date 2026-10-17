package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.Restriction

/** Restricciones de escalada (las mete el admin; las apps las leen). */
interface RestrictionRepository {
    /** En vigor hoy, en toda la app. Se cachea en memoria: cambia muy poco. */
    suspend fun getActive(forceRefresh: Boolean = false): List<Restriction>

    suspend fun getBySchool(schoolId: String): List<Restriction>

    suspend fun adminList(): List<Restriction>

    suspend fun adminSave(id: String?, draft: RestrictionDraft): Restriction

    suspend fun adminDelete(id: String)
}

/** Lo que el admin rellena al crear o editar (fechas "yyyy-MM-dd"). */
data class RestrictionDraft(
    val schoolId: String,
    val sectorBlockId: String?,
    val blockIds: List<String>,
    val kind: String,
    val reason: String,
    val startsOn: String?,
    val endsOn: String?,
    val yearly: Boolean,
    val permanent: Boolean,
    val sourceName: String,
    val sourceUrl: String?,
    val verifiedOn: String?,
)
