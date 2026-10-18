package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.domain.model.UserRewards
import com.meteomontana.android.domain.repository.RewardsRepository

/** Recompensas de cualquiera (para su perfil). */
class GetUserRewardsUseCase(private val repository: RewardsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String): UserRewards = repository.of(uid)
}

class GetMyRewardsUseCase(private val repository: RewardsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): UserRewards = repository.mine()
}

/** climberType "" = quitarlo, null = no tocarlo; titleLevel -1 = quitar, null = no tocarlo. */
class UpdateMyRewardsUseCase(private val repository: RewardsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(climberType: String?, titleLevel: Int?): UserRewards =
        repository.update(climberType, titleLevel)
}
