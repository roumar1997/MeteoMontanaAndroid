package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
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
    val titleHidden: Boolean = false,
    val specialTitle: String? = null,
    val specialColor: String? = null,
    val titleEffect: String? = null,
)

/** climberType "" = quitarlo, null = no tocarlo; titleLevel -1 = quitar, null = no tocarlo; titleHidden null = no tocarlo. */
@Serializable
data class UpdateRewardsDto(val climberType: String? = null, val titleLevel: Int? = null, val featuredAchievement: String? = null,
                            val titleHidden: Boolean? = null)

@Serializable
data class UserBadgeDto(
    val uid: String,
    val verified: Boolean = false,
    val titleLevel: Int = 0,
    val titleHidden: Boolean = false,
    val specialTitle: String? = null,
    val specialColor: String? = null,
    val titleEffect: String? = null,
)

@Serializable
data class BadgesRequestDto(val uids: List<String>)

/** Admin: título especial (vacío = quitarlo) y su color «#RRGGBB». */
@Serializable
data class SpecialTitleDto(val title: String?, val color: String?)

/** Admin: efecto animado («FAIRIES», «DRAGONS»; null o vacío = quitarlo). */
@Serializable
data class TitleEffectDto(val effect: String?)

class KtorRewardsApi(private val client: HttpClient) {
    suspend fun of(uid: String): RewardsDto = client.get("users/$uid/rewards").body()
    suspend fun mine(): RewardsDto = client.get("me/rewards").body()
    suspend fun update(req: UpdateRewardsDto): RewardsDto = client.put("me/rewards") { setBody(req) }.body()
    suspend fun badges(uids: List<String>): List<UserBadgeDto> =
        client.post("users/badges") { setBody(BadgesRequestDto(uids)) }.body()
    suspend fun setSpecialTitle(uid: String, req: SpecialTitleDto): RewardsDto =
        client.put("admin/users/$uid/special-title") { setBody(req) }.body()
    suspend fun setTitleEffect(uid: String, req: TitleEffectDto): RewardsDto =
        client.put("admin/users/$uid/title-effect") { setBody(req) }.body()
}
