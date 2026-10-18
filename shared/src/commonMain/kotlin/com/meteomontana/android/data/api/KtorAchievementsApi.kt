package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

@Serializable
data class AchievementsSyncDto(val ids: List<String>, val unlockedOn: Map<String, String> = emptyMap())

@Serializable
data class AchievementsRarityDto(val totalUsers: Long = 0, val counts: Map<String, Long> = emptyMap())

class KtorAchievementsApi(private val client: HttpClient) {
    /** PUT /api/me/achievements — manda los logros que tengo (solo añade). */
    suspend fun sync(body: AchievementsSyncDto) {
        client.put("me/achievements") { setBody(body) }
    }

    /** GET /api/achievements/rarity — público: cuánta gente tiene cada logro. */
    suspend fun rarity(): AchievementsRarityDto = client.get("achievements/rarity").body()
}
