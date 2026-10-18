package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

@Serializable
data class ContentChangeDto(
    val id: String,
    val entityType: String,
    val entityId: String,
    val blockId: String? = null,
    val schoolId: String? = null,
    val actorUid: String? = null,
    val actorName: String? = null,
    val source: String,
    val contributionId: String? = null,
    val beforeJson: String? = null,
    val afterJson: String? = null,
    val createdAt: String,
)

@Serializable
data class ContributorDto(
    val uid: String,
    val name: String? = null,
    val approved: Int = 0,
    val rejected: Int = 0,
    val pending: Int = 0,
    val lastAt: String? = null,
    val trusted: Boolean = false,
)

@Serializable
data class TrustDto(val trusted: Boolean)

class KtorAdminHistoryApi(private val client: HttpClient) {

    suspend fun history(schoolId: String?, blockId: String?, actorUid: String?, source: String?, before: String?): List<ContentChangeDto> =
        client.get("admin/history") {
            schoolId?.let { parameter("schoolId", it) }
            blockId?.let { parameter("blockId", it) }
            actorUid?.let { parameter("actorUid", it) }
            source?.let { parameter("source", it) }
            before?.let { parameter("before", it) }
        }.body()

    suspend fun restore(changeId: String) {
        client.post("admin/history/$changeId/restore")
    }

    suspend fun contributors(uid: String?): List<ContributorDto> =
        client.get("admin/contributors") { uid?.let { parameter("uid", it) } }.body()

    suspend fun setTrust(uid: String, trusted: Boolean) {
        client.put("admin/contributors/$uid/trust") { setBody(TrustDto(trusted)) }
    }
}
