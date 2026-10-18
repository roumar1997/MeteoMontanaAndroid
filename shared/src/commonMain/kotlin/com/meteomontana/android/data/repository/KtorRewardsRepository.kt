package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorRewardsApi
import com.meteomontana.android.data.api.RewardsDto
import com.meteomontana.android.data.api.UpdateRewardsDto
import com.meteomontana.android.domain.model.UserRewards
import com.meteomontana.android.domain.repository.RewardsRepository

class KtorRewardsRepository(private val api: KtorRewardsApi) : RewardsRepository {
    override suspend fun of(uid: String): UserRewards = api.of(uid).toDomain()
    override suspend fun mine(): UserRewards = api.mine().toDomain()
    override suspend fun update(climberType: String?, titleLevel: Int?, featuredAchievement: String?): UserRewards =
        api.update(UpdateRewardsDto(climberType, titleLevel, featuredAchievement)).toDomain()

    private fun RewardsDto.toDomain() = UserRewards(uid, approved, level, nextLevel, titleLevel, climberType, badges, featuredAchievement)
}
