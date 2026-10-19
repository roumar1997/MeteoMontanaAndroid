package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.domain.model.UserBadge
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

/**
 * climberType "" = quitarlo, null = no tocarlo; titleLevel -1 = quitar, null = no tocarlo; featuredAchievement "" = quitar;
 * titleHidden true = no enseñar ningún título, false = volver a enseñarlo, null = no tocarlo.
 */
class UpdateMyRewardsUseCase(private val repository: RewardsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(climberType: String?, titleLevel: Int?, featuredAchievement: String? = null,
                                titleHidden: Boolean? = null): UserRewards =
        repository.update(climberType, titleLevel, featuredAchievement, titleHidden)
}

/** Verificado (admin) y título de varias personas a la vez: una llamada por pantalla, no una por persona. */
class GetUserBadgesUseCase(private val repository: RewardsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uids: List<String>): List<UserBadge> = repository.badges(uids)
}

/** Solo admin: título especial de alguien (texto libre, vacío = quitarlo) con su color «#RRGGBB». */
class SetSpecialTitleUseCase(private val repository: RewardsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String, title: String?, color: String?): UserRewards =
        repository.setSpecialTitle(uid, title, color)
}
