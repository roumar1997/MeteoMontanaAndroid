package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

@Serializable
data class RewardsDto(
    val uid: String,
    val approved: Int = 0,
    val level: Int = 0,
    val nextLevel: Int? = null,
    val titleLevel: Int = 0,
    val climberType: String? = null,
    val badges: List<String> = emptyList(),
    val featuredAchievement: String? = null,
)

/** climberType "" = quitarlo, null = no tocarlo; titleLevel -1 = quitar, null = no tocarlo. */
@Serializable
data class UpdateRewardsDto(val climberType: String? = null, val titleLevel: Int? = null, val featuredAchievement: String? = null)

class KtorRewardsApi(private val client: HttpClient) {
    suspend fun of(uid: String): RewardsDto = client.get("users/$uid/rewards").body()
    suspend fun mine(): RewardsDto = client.get("me/rewards").body()
    suspend fun update(req: UpdateRewardsDto): RewardsDto = client.put("me/rewards") { setBody(req) }.body()
}
