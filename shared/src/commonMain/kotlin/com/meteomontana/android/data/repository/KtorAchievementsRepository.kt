package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.AchievementsSyncDto
import com.meteomontana.android.data.api.KtorAchievementsApi
import com.meteomontana.android.domain.repository.AchievementRarity
import com.meteomontana.android.domain.repository.AchievementsRepository

class KtorAchievementsRepository(private val api: KtorAchievementsApi) : AchievementsRepository {

    override suspend fun sync(unlockedOn: Map<String, String>) {
        api.sync(AchievementsSyncDto(unlockedOn.keys.toList(), unlockedOn))
    }

    override suspend fun rarity(): AchievementRarity {
        val r = api.rarity()
        val total = r.totalUsers.toInt()
        // Redondeo normal; sin nadie sincronizado no hay porcentaje.
        val pct = if (total <= 0) emptyMap() else r.counts.mapValues { (_, n) -> ((n * 100.0) / total + 0.5).toInt().coerceIn(0, 100) }
        return AchievementRarity(total, pct)
    }
}
