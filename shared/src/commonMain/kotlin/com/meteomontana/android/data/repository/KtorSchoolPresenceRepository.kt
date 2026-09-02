package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorSchoolPresenceApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.repository.SchoolPresenceRepository

/**
 * Adaptador Ktor del puerto [SchoolPresenceRepository]. Sin caché local: es
 * información en vivo (quién hay AHORA), no tiene sentido offline como las
 * quedadas o el catálogo de piedras.
 */
class KtorSchoolPresenceRepository(
    private val api: KtorSchoolPresenceApi,
) : SchoolPresenceRepository {

    override suspend fun getActivePresence(schoolId: String): List<SchoolPresence> =
        api.getActivePresence(schoolId).map { it.toDomain() }

    override suspend fun markPresence(schoolId: String): SchoolPresence =
        api.markPresence(schoolId).toDomain()

    override suspend fun clearPresence(schoolId: String) {
        api.clearPresence(schoolId)
    }
}
