package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.ContentChangeDto
import com.meteomontana.android.data.api.KtorAdminHistoryApi
import com.meteomontana.android.domain.model.ContentChangeItem
import com.meteomontana.android.domain.model.ContributorItem
import com.meteomontana.android.domain.repository.AdminHistoryRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class KtorAdminHistoryRepository(private val api: KtorAdminHistoryApi) : AdminHistoryRepository {

    override suspend fun history(schoolId: String?, blockId: String?, actorUid: String?, source: String?,
                                 before: String?): List<ContentChangeItem> =
        api.history(schoolId, blockId, actorUid, source, before).map { it.toDomain() }

    override suspend fun restore(changeId: String) = api.restore(changeId)

    override suspend fun contributors(uid: String?): List<ContributorItem> =
        api.contributors(uid).map { ContributorItem(it.uid, it.name, it.approved, it.rejected, it.pending, it.lastAt, it.trusted) }

    override suspend fun setTrust(uid: String, trusted: Boolean) = api.setTrust(uid, trusted)

    private fun ContentChangeDto.toDomain() = ContentChangeItem(id, entityType, entityId, blockId, schoolId, actorUid,
        actorName, source, contributionId, fields(beforeJson), fields(afterJson), createdAt)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** JSON plano del estado → campo: valor en texto (los nulos no salen). */
        internal fun fields(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
            return obj.entries.mapNotNull { (k, v) ->
                when (v) {
                    is JsonNull -> null
                    is JsonPrimitive -> k to v.content
                    else -> k to v.toString()
                }
            }.toMap(LinkedHashMap())
        }
    }
}
