package com.meteomontana.android.domain.usecase.journal

import com.meteomontana.android.domain.repository.AchievementRarity
import com.meteomontana.android.domain.repository.AchievementsRepository

/** Manda al servidor los logros que tengo para que cuente cuánta gente tiene cada uno. Solo los míos. */
class SyncMyAchievementsUseCase(private val repository: AchievementsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(achievements: List<Achievement>) {
        repository.sync(achievements.filter { it.unlocked }.associate { it.id to (it.unlockedOn ?: "") })
    }
}

/** Cuánta gente tiene cada logro. */
class GetAchievementRarityUseCase(private val repository: AchievementsRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): AchievementRarity = repository.rarity()
}
