package com.meteomontana.android.domain.repository

/** Cuánta gente tiene cada logro (porcentaje entero 0..100) y cuántos escaladores cuentan para ese porcentaje. */
data class AchievementRarity(val totalUsers: Int, val percentById: Map<String, Int>)

interface AchievementsRepository {
    /** Manda al servidor los logros que tengo ("id" → día). Solo añade. */
    suspend fun sync(unlockedOn: Map<String, String>)

    suspend fun rarity(): AchievementRarity
}
