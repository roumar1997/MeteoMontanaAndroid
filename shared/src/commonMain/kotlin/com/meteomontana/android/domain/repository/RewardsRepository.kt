package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.UserRewards

/** Recompensas: las de cualquiera (perfil) y las mías (elegir título y viero/bloquero). */
interface RewardsRepository {
    suspend fun of(uid: String): UserRewards
    suspend fun mine(): UserRewards
    /** climberType "" = quitarlo, null = no tocarlo; titleLevel -1 = quitar, null = no tocarlo. */
    suspend fun update(climberType: String?, titleLevel: Int?): UserRewards
}
