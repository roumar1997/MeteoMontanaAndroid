package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorRestrictionApi
import com.meteomontana.android.data.api.dto.RestrictionDto
import com.meteomontana.android.data.api.dto.RestrictionInputDto
import com.meteomontana.android.domain.model.Restriction
import com.meteomontana.android.domain.model.RestrictionKind
import com.meteomontana.android.domain.repository.RestrictionDraft
import com.meteomontana.android.domain.repository.RestrictionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KtorRestrictionRepository(private val api: KtorRestrictionApi) : RestrictionRepository {

    private val mutex = Mutex()
    private var cached: List<Restriction>? = null

    override suspend fun getActive(forceRefresh: Boolean): List<Restriction> = mutex.withLock {
        cached?.takeUnless { forceRefresh } ?: api.active().map { it.toDomain() }.also { cached = it }
    }

    override suspend fun getBySchool(schoolId: String): List<Restriction> =
        api.bySchool(schoolId).map { it.toDomain() }

    override suspend fun adminList(): List<Restriction> = api.adminList().map { it.toDomain() }

    override suspend fun adminSave(id: String?, draft: RestrictionDraft): Restriction {
        val body = draft.toDto()
        val saved = if (id == null) api.adminCreate(body) else api.adminUpdate(id, body)
        invalidate()
        return saved.toDomain()
    }

    override suspend fun adminDelete(id: String) {
        api.adminDelete(id)
        invalidate()
    }

    private suspend fun invalidate() = mutex.withLock { cached = null }

    private fun RestrictionDto.toDomain() = Restriction(
        id = id, schoolId = schoolId, sectorBlockId = sectorBlockId, blockIds = blockIds,
        kind = RestrictionKind.parse(kind), reason = reason, startsOn = startsOn, endsOn = endsOn,
        yearly = yearly, permanent = permanent, sourceName = sourceName, sourceUrl = sourceUrl,
        verifiedOn = verifiedOn,
    )

    private fun RestrictionDraft.toDto() = RestrictionInputDto(
        schoolId = schoolId, sectorBlockId = sectorBlockId, blockIds = blockIds, kind = kind, reason = reason,
        startsOn = startsOn, endsOn = endsOn, yearly = yearly, permanent = permanent, sourceName = sourceName,
        sourceUrl = sourceUrl, verifiedOn = verifiedOn,
    )
}
